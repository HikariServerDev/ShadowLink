#!/usr/bin/env bash
# Builds every supported version and collects the mod JARs in ./build.
set -euo pipefail
cd "$(dirname "$0")"

mapfile -t versions < supported-versions.txt

staging=$(mktemp -d "$PWD/.build-XXXXXXXX")
trap 'rm -rf "$staging"' EXIT
for version in "${versions[@]}"; do
    echo "=== ShadowLink $version"
    (cd "$version" && ./gradlew clean build --console=plain --no-daemon --max-workers=2 -Dorg.gradle.parallel=false)
    mod_version=$(sed -n 's/^mod_version=//p' "$version/gradle.properties")
    jar="$version/build/libs/ShadowLink-$version-$mod_version.jar"
    test -f "$jar"
    cp "$jar" "$staging/"
done
python3 scripts/verify-artifacts.py --directory "$staging" --write-manifest --self-test
# Keep the previous complete set until every build and regression check has succeeded.
mkdir -p build
rm -f build/ShadowLink-*.jar
cp "$staging/"* build/
echo
ls -l build
