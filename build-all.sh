#!/usr/bin/env bash
# One shared build and verification entry point; output is build/distributions/.
set -euo pipefail
cd "$(dirname "$0")"

exec ./gradlew chiseledBuild --console=plain --no-daemon --max-workers=2 -Dorg.gradle.parallel=false "$@"
