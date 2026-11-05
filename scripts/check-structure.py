#!/usr/bin/env python3
"""Reject retained independent source trees, duplicate managed sources and stale build paths."""

import hashlib
import json
from pathlib import Path
import re
import subprocess

from project_model import target_properties

root = Path(__file__).resolve().parent.parent
targets = target_properties(root)
for version in targets:
    if (root / version).exists():
        raise SystemExit(f'Obsolete version directory still exists: {version}/')
for file in ['gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'build.gradle',
             'settings.gradle', 'stonecutter.gradle', 'src/main/resources/fabric.mod.json',
             'src/main/resources/shadowlink.mixins.json', '.github/workflows/ci.yml']:
    if not (root / file).is_file():
        raise SystemExit(f'Missing shared project file: {file}')
baseline = json.loads((root / 'docs/migration/baseline.json').read_text())
if list(baseline['targets']) != list(targets):
    raise SystemExit('Frozen migration baseline does not cover exactly the supported targets')
settings = (root / 'settings.gradle').read_text()
controller = (root / 'stonecutter.gradle').read_text()
vcs = re.search(r"vcsVersion\s*=\s*['\"]([^'\"]+)", settings).group(1)
active = re.search(r"stonecutter\.active\s*['\"]([^'\"]+)", controller).group(1)
if vcs != active or active not in targets:
    raise SystemExit('VCS version, IDE active version and supported targets disagree')

tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
sources = sorted(path.relative_to(root).as_posix() for directory in ['src/main/java', 'src/test/java', 'test-support']
                 for path in (root / directory).rglob('*.java'))
expected_sources = {'src/' + name for target in baseline['targets'].values() for name in target['source_tokens']}
actual_sources = {path for path in sources if path.startswith('src/')}
if actual_sources != expected_sources:
    raise SystemExit('Shared source tree has missing or unexpected production/regression files')
seen = {}
for path in sources:
    if not path.startswith(('src/main/java/', 'src/test/java/', 'test-support/')):
        raise SystemExit(f'Java source outside the shared source/test trees: {path}')
    digest = hashlib.sha256((root / path).read_bytes()).hexdigest()
    if digest in seen:
        raise SystemExit(f'Duplicate managed source: {seen[digest]} and {path}')
    seen[digest] = path
for path in tracked:
    if path.endswith('.jar') and path != 'gradle/wrapper/gradle-wrapper.jar':
        raise SystemExit(f'Build artifact still tracked as source: {path}')
old_path = re.compile(r'(?<![\w/])(?:' + '|'.join(re.escape(v) for v in targets) + r')/(?:src|gradle|build|gradlew)(?:/|\b)')
files = list(root.glob('*.gradle')) + list((root / 'gradle').glob('*.gradle'))
files += list((root / 'scripts').glob('*.py')) + list((root / '.github').rglob('*.yml'))
for path in files:
    if path.is_file() and old_path.search(path.read_text()):
        raise SystemExit(f'Stale independent-project path reference: {path.relative_to(root)}')
print(f'Structure verified: {len(targets)} targets, one managed main/test source tree, active/VCS {active}')
