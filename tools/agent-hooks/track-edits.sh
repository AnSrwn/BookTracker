#!/usr/bin/env bash
# PostToolUse hook (Claude: Edit|Write|MultiEdit|Bash, Codex: apply_patch|Edit|Write|Bash).
# Remembers per session that it changed something under app/, so the turn-end check runs only for
# sessions that touched the app (D40). A Bash command counts when it runs in app/ or names a path
# under app/; a read-only command may count too, which only costs one check.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

input="$(cat)"
cwd="$(input_cwd "$input")"
touched=false

while IFS= read -r path; do
    [ -n "$path" ] || continue
    case "$(absolute_path "$path" "$cwd")" in "$repo_root"/app/*) touched=true ;; esac
done < <(edited_paths "$input")

if [ "$(jq -r '.tool_name // ""' <<<"$input")" = "Bash" ]; then
    case "$cwd/" in "$repo_root"/app/*) touched=true ;; esac
    grep -Eq '(^|[^[:alnum:]_.-])app/' <<<"$(bash_command "$input")" && touched=true
fi

if $touched; then
    mkdir -p "$state_dir"
    touch "$state_dir/touched-$(session_id "$input")"
fi
exit 0
