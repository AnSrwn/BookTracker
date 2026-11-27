#!/usr/bin/env bash
# Runs the ktlint version pinned in this folder (D31). On first use it downloads that version into a
# cache outside Git and checks its SHA-256, so every clone and both agents use the same ktlint.
#
#   tools/ktlint/ktlint.sh app          check all Kotlin files under app/
#   tools/ktlint/ktlint.sh -F app       format them
#   tools/ktlint/ktlint.sh -F a.kt b.kt format single files
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
version="$(tr -d '[:space:]' < "$here/version")"
expected="$(tr -d '[:space:]' < "$here/sha256")"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/booktracker/ktlint/$version"
bin="$cache/ktlint"

if [ ! -x "$bin" ]; then
    mkdir -p "$cache"
    # Each run downloads into its own file and moves it into place atomically, so parallel hooks on
    # first use cannot read or delete each other's half-written download
    download="$(mktemp "$cache/ktlint.download.XXXXXX")"
    trap 'rm -f "$download"' EXIT
    if ! curl -fsSL -o "$download" "https://github.com/pinterest/ktlint/releases/download/$version/ktlint"; then
        echo "ktlint $version: download failed" >&2
        exit 1
    fi
    actual="$(shasum -a 256 "$download" | cut -d' ' -f1)"
    if [ "$actual" != "$expected" ]; then
        echo "ktlint $version: checksum mismatch (expected $expected, got $actual)" >&2
        exit 1
    fi
    chmod +x "$download"
    mv -f "$download" "$bin"
    trap - EXIT
fi

# A directory argument becomes the Kotlin files inside it, without build output
args=()
for arg in "$@"; do
    if [ -d "$arg" ]; then
        args+=("${arg%/}/**/*.kt" "${arg%/}/**/*.kts" "!**/build/**" "!**/.gradle/**")
    else
        args+=("$arg")
    fi
done

# bash 3.2 (macOS) treats an empty array as unbound under `set -u`
if [ ${#args[@]} -eq 0 ]; then
    exec "$bin" --relative
fi
exec "$bin" --relative "${args[@]}"
