#!/bin/bash

# Increments the patch/build component of gradle.properties' version, builds the
# IntelliJ plugin ZIP under build/distributions/, archives a copy under
# releases/ and commits both. If anything fails the version bump is rolled back,
# so a failed run does not leave gradle.properties pointing at a version that
# was never built.
set -euo pipefail

cd "$(dirname "$0")"

plugin_name="CreditPincher"
properties_file="gradle.properties"
distribution_directory="build/distributions"
releases_dir="releases"

current_version="$(awk -F '=' '/^[[:space:]]*version[[:space:]]*=/ { gsub(/[[:space:]]/, "", $2); print $2; exit }' "$properties_file")"

if [[ ! "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Expected a numeric major.minor.build version in $properties_file; found: ${current_version:-<none>}" >&2
    exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
build="${BASH_REMATCH[3]}"
next_version="$major.$minor.$((build + 1))"

# Keep a pristine copy of the properties so the bump can be undone on failure.
original_properties="$(mktemp "${properties_file}.orig.XXXXXX")"
cp "$properties_file" "$original_properties"
temporary_properties=""
release_committed=0

cleanup() {
    if [[ "$release_committed" -ne 1 && -f "$original_properties" ]]; then
        cp "$original_properties" "$properties_file"
        echo "Rolled $properties_file back to version $current_version." >&2
    fi
    rm -f "$original_properties" ${temporary_properties:+"$temporary_properties"}
}
trap cleanup EXIT

temporary_properties="$(mktemp "${properties_file}.XXXXXX")"
awk -v version="$next_version" '
    /^[[:space:]]*version[[:space:]]*=/ && !updated {
        sub(/=.*/, "= " version)
        updated = 1
    }
    { print }
    END { if (!updated) exit 1 }
' "$properties_file" > "$temporary_properties"
mv "$temporary_properties" "$properties_file"
temporary_properties=""

if [[ -d "$distribution_directory" ]]; then
    find "$distribution_directory" -maxdepth 1 -type f -name '*.zip' -delete
fi

echo "Building $plugin_name version $next_version..."
./gradlew buildPlugin

built_zip="$distribution_directory/$plugin_name-$next_version.zip"

if [[ ! -f "$built_zip" ]]; then
    echo "Error: Expected plugin archive not found at $built_zip" >&2
    echo "Archives actually produced:" >&2
    find "$distribution_directory" -maxdepth 1 -type f -name '*.zip' >&2 || true
    exit 1
fi

echo "Plugin built: $built_zip"

mkdir -p "$releases_dir"
cp "$built_zip" "$releases_dir/"
echo "Archived to $releases_dir/$plugin_name-$next_version.zip"

if ! git add "$properties_file" "$releases_dir/$plugin_name-$next_version.zip"; then
    echo "Error: Failed to stage files for commit" >&2
    exit 1
fi

if ! git commit -m "Release version $next_version"; then
    echo "Error: Failed to commit release" >&2
    exit 1
fi

release_committed=1

echo "Committed release $next_version."
echo "Note: pushing this commit triggers the Release workflow, which publishes"
echo "its own v1.0.<run number> tag independently of this version."
