#!/usr/bin/env bash
# PostToolUse hook (Claude: Edit|Write|MultiEdit, Codex: apply_patch|Edit|Write).
# Formats the Kotlin files under app/ that the tool just changed, with the pinned ktlint (D26).
# What ktlint cannot fix goes back to the agent as feedback (exit 2). If ktlint itself cannot run
# (no network on first use, checksum mismatch), that is reported as a hook error, not as a lint finding.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

input="$(cat)"
cwd="$(input_cwd "$input")"
files=()
while IFS= read -r path; do
    [ -n "$path" ] || continue
    path="$(absolute_path "$path" "$cwd")"
    case "$path" in
        "$repo_root"/app/*.kt | "$repo_root"/app/*.kts) ;;
        *) continue ;;
    esac
    case "$path" in */build/*) continue ;; esac
    [ -f "$path" ] && files+=("$path")
done < <(edited_paths "$input")

[ ${#files[@]} -eq 0 ] && exit 0

if ! setup="$("$repo_root/tools/ktlint/ktlint.sh" --version 2>&1)"; then
    echo "ktlint could not run, the file was not formatted: $setup" >&2
    exit 1
fi

if ! output="$(cd "$repo_root" && tools/ktlint/ktlint.sh -F "${files[@]}" 2>&1)"; then
    {
        echo "ktlint formatted the file, but these problems need a manual fix:"
        echo "$output"
    } >&2
    exit 2
fi
exit 0
