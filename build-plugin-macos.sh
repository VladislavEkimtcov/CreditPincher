#!/bin/bash

# Increments the patch/build component of gradle.properties' version, then
# creates the IntelliJ plugin ZIP under build/distributions/.
set -euo pipefail

cd "$(dirname "$0")"

properties_file="gradle.properties"
current_version="$(awk -F '=' '/^[[:space:]]*version[[:space:]]*=/ { gsub(/[[:space:]]/, "", $2); print $2; exit }' "$properties_file")"

if [[ ! "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Expected a numeric major.minor.build version in $properties_file; found: ${current_version:-<none>}" >&2
    exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
build="${BASH_REMATCH[3]}"
next_version="$major.$minor.$((build + 1))"

temporary_properties="$(mktemp "${properties_file}.XXXXXX")"
trap 'rm -f "$temporary_properties"' EXIT

awk -v version="$next_version" '
    /^[[:space:]]*version[[:space:]]*=/ && !updated {
        sub(/=.*/, "= " version)
        updated = 1
    }
    { print }
    END { if (!updated) exit 1 }
' "$properties_file" > "$temporary_properties"
mv "$temporary_properties" "$properties_file"

distribution_directory="build/distributions"
if [[ -d "$distribution_directory" ]]; then
    find "$distribution_directory" -maxdepth 1 -type f -name '*.zip' -delete
fi

echo "Building CreditPincher version $next_version..."
./gradlew buildPlugin

echo "Plugin built: $distribution_directory/CreditPincher-$next_version.zip"

built_zip="$distribution_directory/CreditPincher-$next_version.zip"
releases_dir="releases"

if [[ ! -f "$built_zip" ]]; then
    echo "Error: Expected plugin archive not found at $built_zip" >&2
    exit 1
fi

if ! mkdir -p "$releases_dir"; then
    echo "Error: Failed to create releases directory at $releases_dir" >&2
    exit 1
fi

if ! cp "$built_zip" "$releases_dir/"; then
    echo "Error: Failed to copy $built_zip to $releases_dir/" >&2
    exit 1
fi

if ! git add "$properties_file" "$releases_dir/CreditPincher-$next_version.zip"; then
    echo "Error: Failed to stage files for commit" >&2
    exit 1
fi

if ! git commit -m "Release version $next_version"; then
    echo "Error: Failed to commit release" >&2
    exit 1
fi

built_zip="$distribution_directory/CreditPincher-$next_version.zip"
releases_dir="releases"

if [[ ! -f "$built_zip" ]]; then
    echo "Error: Expected plugin archive not found at $built_zip" >&2
    exit 1
fi

if ! mkdir -p "$releases_dir"; then
    echo "Error: Failed to create releases directory at $releases_dir" >&2
    exit 1
fi

if ! cp "$built_zip" "$releases_dir/"; then
    echo "Error: Failed to copy $built_zip to $releases_dir/" >&2
    exit 1
fi
