#!/usr/bin/env python3
"""Collect verified target outputs locally or from independent CI matrix jobs."""

import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile

from project_model import target_properties

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--input', type=Path, required=True)
parser.add_argument('--output', type=Path, default=Path('build/distributions'))
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
targets = target_properties(root)
expected = {f"ShadowLink-{version}-{values['mod_version']}.jar" for version, values in targets.items()}
jars = list(args.input.glob('*/*.jar'))
if len(jars) != len(expected) or {path.name for path in jars} != expected:
    raise SystemExit('Collection requires exactly one verified JAR for each supported Minecraft target')

spec = importlib.util.spec_from_file_location('artifact_verifier', root / 'scripts/verify-artifacts.py')
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)
args.output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='distribution-stage-', dir=args.output.parent) as temporary:
    staging = Path(temporary) / 'distribution'
    staging.mkdir()
    for jar in jars:
        shutil.copy2(jar, staging / jar.name)
    manifest, checksums = verifier.verify_collection(staging, root, targets, check_manifest=False)
    (staging / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (staging / 'SHA256SUMS').write_text(checksums)
    rejected = verifier.negative_checks(staging, root, targets)
    verifier.verify_collection(staging, root, targets)
    backup = Path(temporary) / 'previous'
    if args.output.exists():
        args.output.rename(backup)
    try:
        staging.rename(args.output)
    except OSError:
        if backup.exists():
            backup.rename(args.output)
        raise
print(f'Collected {len(jars)} validated JARs; rejected {rejected} invalid artifact sets: {args.output}')
