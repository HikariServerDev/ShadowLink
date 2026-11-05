#!/usr/bin/env python3
"""Check the compiler's actual source/dependency inputs against the frozen migration baseline."""

import argparse
import json
from pathlib import Path

from project_model import target_properties, verify_effective_model

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--version', required=True)
parser.add_argument('--model', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
properties = target_properties(root)[args.version]
model = json.loads(args.model.read_text())
if model['minecraft'] != args.version or str(model['java']) != str(properties['java_version']):
    raise SystemExit('Verification model targets the wrong Minecraft/Java version')
try:
    classes = verify_effective_model(root, args.version, model)
except ValueError as error:
    raise SystemExit(f'Migration source verification failed: {error}') from error
print(f'Effective sources and resolved dependencies verified: {args.version}, {len(classes)} production classes')
