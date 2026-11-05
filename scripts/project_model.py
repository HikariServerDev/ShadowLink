"""Shared configuration and effective-source checks for the multi-version build."""

import hashlib
import json
from pathlib import Path
import re


# Keep literals intact while ignoring whitespace and comments, including Stonecutter markers.
JAVA_TOKEN = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|\s+|[\w$]+|.', re.UNICODE)


def java_tokens(text):
    return [token for token in JAVA_TOKEN.findall(text)
            if not token.isspace() and not token.startswith(("//", "/*"))]


def source_fingerprint(text):
    return hashlib.sha256(json.dumps(java_tokens(text), ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()


def source_key(path, role):
    tokens = java_tokens(path.read_text())
    if not tokens:
        return None
    start = tokens.index("package") + 1
    end = tokens.index(";", start)
    package = "".join(tokens[start:end]).replace(".", "/")
    return f"{role}/java/{package}/{path.name}"


def properties(path):
    return dict(line.strip().split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def target_properties(root):
    versions = (root / "supported-versions.txt").read_text().splitlines()
    if not versions or len(versions) != len(set(versions)) or any(not re.fullmatch(r'\d+\.\d+(?:\.\d+)?', v) for v in versions):
        raise ValueError('Empty, duplicate or invalid Minecraft target list')
    catalog = json.loads((root / "gradle/targets.json").read_text())
    if list(catalog) != versions:
        raise ValueError("Target catalog and supported-versions.txt differ in versions/order")
    common = properties(root / "gradle.properties")
    return {version: {**common, **target, "minecraft_version": version} for version, target in catalog.items()}


def matching_mixins(root, version):
    def version_tuple(value):
        return tuple(int(part) for part in value.split('.')) + (0,) * (3 - len(value.split('.')))

    def matches(condition):
        if condition == 'true':
            return True
        for predicate in condition.split(' && '):
            match = re.fullmatch(r'(>=|<)([\d.]+)', predicate)
            if not match:
                raise ValueError(f'Unsupported mixin selection rule: {condition}')
            left, right = version_tuple(version), version_tuple(match[2])
            if not (left >= right if match[1] == '>=' else left < right):
                return False
        return True

    rules = json.loads((root / 'gradle/mixins.json').read_text())
    return [name for name, condition in rules.items() if matches(condition)]


def verify_effective_model(root, version, model):
    baseline = json.loads((root / "docs/migration/baseline.json").read_text())["targets"][version]
    sources = {}
    for role, paths in model["source_files"].items():
        for filename in paths:
            path = root / filename
            # Test-support is a separate source set input; the existing core regression stays shared.
            if role == "test" and path.name != "ShadowRegression.java":
                continue
            key = source_key(path, role)
            if key is not None:
                if key in sources:
                    raise ValueError(f"Duplicate effective production/test source: {key}")
                sources[key] = source_fingerprint(path.read_text())
    if sources != baseline["source_tokens"]:
        names = set(sources) | set(baseline["source_tokens"])
        changed = sorted(name for name in names if sources.get(name) != baseline["source_tokens"].get(name))
        raise ValueError(f"{version}: effective source tokens changed from migration baseline: {changed}")
    if model["dependencies"] != baseline["dependencies"]:
        raise ValueError(f"{version}: resolved runtime dependencies changed")
    expected = set(baseline["jar"]["classes"])
    if set(model["classes"]) != expected:
        raise ValueError(f"{version}: production class set differs from migration baseline")
    return expected
