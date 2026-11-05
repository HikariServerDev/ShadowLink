#!/usr/bin/env python3
"""Validate the complete versioned JAR set before collecting it for distribution."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import tempfile
import zipfile
import zlib

from project_model import matching_mixins, target_properties


class VerificationError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def supported_projects(root):
    versions = (root / "supported-versions.txt").read_text().splitlines()
    require(versions and len(versions) == len(set(versions)), "Empty or duplicate supported versions")
    require(all(re.fullmatch(r"\d+\.\d+(?:\.\d+)?", version) for version in versions), "Invalid version list")
    result = target_properties(root)
    require(set(result) == set(versions), 'Target catalog differs from supported-versions.txt')
    require(len({values["mod_version"] for values in result.values()}) == 1, "Mod versions are inconsistent")
    readme_jars = Counter(re.findall(r"ShadowLink-[\d.]+-[\d.]+\.jar", (root / "README.md").read_text()))
    expected_jars = Counter({f"ShadowLink-{v}-{p['mod_version']}.jar": 2 for v, p in result.items()})
    require(readme_jars == expected_jars, "English/Japanese README artifact lists are inconsistent")
    return result


def verify_jar(path, root, version, values):
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            files = {name for name in names if not name.endswith("/")}
            require(len(names) == len(set(names)), f"{path.name}: duplicate ZIP entries")
            require(archive.testzip() is None, f"{path.name}: ZIP CRC mismatch")
            required = {"fabric.mod.json", "shadowlink.mixins.json", "LICENSE", "META-INF/MANIFEST.MF",
                        "assets/shadowlink/icon.png", f"ShadowLink-{version}-refmap.json"}
            require(required <= files, f"{path.name}: missing required entries: {sorted(required - files)}")
            require(archive.read("LICENSE") == (root / "LICENSE").read_bytes(), f"{path.name}: license mismatch")
            source = (root / "src/main/resources/fabric.mod.json").read_text()
            for key, value in {"version": values["mod_version"], **values}.items():
                source = source.replace("${" + key + "}", str(value))
            metadata = json.loads(archive.read("fabric.mod.json"))
            require(metadata == json.loads(source), f"{path.name}: wrong mod metadata")
            require(metadata["license"] == "LGPL-3.0-only", f"{path.name}: missing license metadata")
            config = json.loads(archive.read("shadowlink.mixins.json"))
            template = (root / 'src/main/resources/shadowlink.mixins.json').read_text()
            expected_config = json.loads(template.replace('${java_version}', str(values['java_version']))
                                         .replace('${mixin_classes}', json.dumps(matching_mixins(root, version))))
            expected_config['refmap'] = f'ShadowLink-{version}-refmap.json'
            require(config == expected_config, f'{path.name}: wrong mixin selection/configuration')
            require(config["required"] is True and config["injectors"]["defaultRequire"] >= 1,
                    f"{path.name}: mixin failure was made optional")
            require(config["refmap"] == f"ShadowLink-{version}-refmap.json", f"{path.name}: wrong refmap")
            for mixin in config["mixins"]:
                name = config["package"].replace(".", "/") + "/" + mixin + ".class"
                require(name in files, f"{path.name}: missing mixin class {name}")
            refmap = json.loads(archive.read(config["refmap"]))
            mappings = refmap["mappings"]
            require(bool(mappings), f"{path.name}: empty refmap")
            require(refmap["data"].get("named:intermediary") == mappings,
                    f"{path.name}: refmap namespace mappings differ")
            expected_major = int(metadata["depends"]["java"][2:]) + 44
            expected_classes = set(json.loads((root / 'docs/migration/baseline.json').read_text())['targets'][version]['jar']['classes'])
            require({name for name in files if name.endswith('.class')} == expected_classes,
                    f'{path.name}: wrong production class set')
            for name in files - required:
                require(name.endswith(".class"), f"{path.name}: unexpected resource {name}")
                # Nested classes must belong to a production source, not src/test or test-support.
                outer = name[:-6].split("$", 1)[0] + ".java"
                require((root / "src/main/java" / outer).is_file(),
                        f"{path.name}: non-production class {name}")
                data = archive.read(name)
                require(data[:4] == b"\xca\xfe\xba\xbe", f"{path.name}: invalid class {name}")
                require(struct.unpack(">H", data[6:8])[0] == expected_major,
                        f"{path.name}: wrong Java target for {name}")
    except (zipfile.BadZipFile, zlib.error, EOFError, KeyError, ValueError, struct.error) as error:
        raise VerificationError(f"{path.name}: invalid JAR: {error}") from error
    return {"minecraft": version, "mod_version": values["mod_version"], "file": path.name,
            "size": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "fabric_loader": metadata["depends"]["fabricloader"], "java": metadata["depends"]["java"],
            "mappings": values.get("yarn_mappings", "Legacy Yarn build " + values.get("yarn_build", ""))}


def verify_collection(directory, root, projects, check_manifest=True):
    expected = {f"ShadowLink-{version}-{values['mod_version']}.jar" for version, values in projects.items()}
    actual = {p.name for p in directory.iterdir()}
    require(actual - {"manifest.json", "SHA256SUMS"} == expected,
            f"Artifact set mismatch: missing={sorted(expected - actual)}, extra={sorted(actual - expected - {'manifest.json', 'SHA256SUMS'})}")
    artifacts = [verify_jar(directory / f"ShadowLink-{v}-{p['mod_version']}.jar", root, v, p)
                 for v, p in projects.items()]
    manifest = {"schema_version": 1, "artifacts": artifacts}
    checksums = "".join(f"{artifact['sha256']}  {artifact['file']}\n" for artifact in artifacts)
    if check_manifest:
        require((directory / "manifest.json").is_file() and (directory / "SHA256SUMS").is_file(),
                "Missing manifest.json or SHA256SUMS")
        require(json.loads((directory / "manifest.json").read_text()) == manifest, "Manifest differs from actual JARs")
        require((directory / "SHA256SUMS").read_text() == checksums, "SHA256SUMS differs from actual JARs")
    return manifest, checksums


def replace_zip_entry(path, name, data):
    replacement = path.with_suffix(".tmp")
    with zipfile.ZipFile(path) as original, zipfile.ZipFile(replacement, "w", zipfile.ZIP_DEFLATED) as changed:
        for entry in original.infolist():
            if entry.filename != name:
                changed.writestr(entry, original.read(entry.filename))
        if data is not None:
            changed.writestr(name, data)
    replacement.replace(path)


def negative_checks(directory, root, projects):
    """Mutate disposable copies of real JARs and check that the distribution gate rejects them."""
    name = next(iter(projects))
    jar_name = f"ShadowLink-{name}-{projects[name]['mod_version']}.jar"
    cases = ["missing_version", "extra_version", "wrong_metadata", "missing_license", "missing_refmap", "wrong_namespace",
             "test_class", "truncated_zip", "bad_crc", "wrong_manifest", "wrong_checksums"]
    for case in cases:
        with tempfile.TemporaryDirectory(prefix="shadowlink-invalid-") as temp:
            copy = Path(temp)
            for path in directory.iterdir():
                shutil.copy2(path, copy / path.name)
            jar = copy / jar_name
            if case == "missing_version":
                jar.unlink()
            elif case == "extra_version":
                shutil.copy2(jar, copy / "ShadowLink-9.99-1.0.0.jar")
            elif case == "wrong_metadata":
                with zipfile.ZipFile(jar) as archive:
                    metadata = json.loads(archive.read("fabric.mod.json"))
                metadata["depends"]["minecraft"] = "9.99"
                replace_zip_entry(jar, "fabric.mod.json", json.dumps(metadata).encode())
            elif case == "missing_license":
                replace_zip_entry(jar, "LICENSE", None)
            elif case == "missing_refmap":
                replace_zip_entry(jar, f"ShadowLink-{name}-refmap.json", None)
            elif case == "wrong_namespace":
                refmap_name = f"ShadowLink-{name}-refmap.json"
                with zipfile.ZipFile(jar) as archive:
                    refmap = json.loads(archive.read(refmap_name))
                refmap["data"]["named:intermediary"] = {}
                replace_zip_entry(jar, refmap_name, json.dumps(refmap).encode())
            elif case == "test_class":
                replace_zip_entry(jar, "dev/atsukimc/shadowlink/core/ShadowRegression.class", b"unexpected")
            elif case == "truncated_zip":
                jar.write_bytes(jar.read_bytes()[:100])
            elif case == "bad_crc":
                with zipfile.ZipFile(jar) as archive:
                    entry = archive.getinfo("LICENSE")
                data = bytearray(jar.read_bytes())
                # The local header encodes filename/extra lengths before the compressed data.
                filename_length, extra_length = struct.unpack_from("<HH", data, entry.header_offset + 26)
                start = entry.header_offset + 30 + filename_length + extra_length
                data[start + entry.compress_size // 2] ^= 1
                jar.write_bytes(data)
            elif case == "wrong_manifest":
                (copy / "manifest.json").write_text("{}\n")
            elif case == "wrong_checksums":
                (copy / "SHA256SUMS").write_text("invalid\n")
            try:
                verify_collection(copy, root, projects)
            except VerificationError:
                continue
            raise AssertionError(f"Invalid distribution accepted: {case}")
    return len(cases)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path("build/distributions"))
    parser.add_argument("--write-manifest", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument('--version', help='Validate one CI target instead of the complete distribution')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    projects = supported_projects(root)
    if args.version:
        require(args.version in projects, f'Unsupported Minecraft target: {args.version}')
        projects = {args.version: projects[args.version]}
    manifest, checksums = verify_collection(args.directory, root, projects, not args.write_manifest)
    if args.write_manifest:
        (args.directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        (args.directory / "SHA256SUMS").write_text(checksums)
    rejected = negative_checks(args.directory, root, projects) if args.self_test else 0
    print(f"Verified {len(manifest['artifacts'])} versioned JARs; negative artifact cases rejected: {rejected}")


if __name__ == "__main__":
    try:
        main()
    except VerificationError as error:
        raise SystemExit(f"Artifact verification failed: {error}") from error
