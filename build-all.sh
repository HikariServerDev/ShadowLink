#!/usr/bin/env bash
# Builds every supported version and collects the mod JARs in ./build.
set -euo pipefail
cd "$(dirname "$0")"

versions=(1.12.2 1.16 1.16.1 1.16.2 1.16.3 1.16.4 1.16.5 1.18 1.18.1 1.18.2)

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
# Keep the previous complete set until every build and regression check has succeeded.
mkdir -p build
rm -f build/ShadowLink-*.jar
cp "$staging/"*.jar build/
echo
ls -l build
