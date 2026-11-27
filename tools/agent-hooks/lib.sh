# Shared by the agent hooks. The same scripts run for Claude Code (.claude/settings.json) and
# Codex (.codex/hooks.json); both send JSON on stdin and accept the same JSON answers we use.

repo_root="$(git rev-parse --show-toplevel)"
state_dir="$repo_root/app/build/agent-hooks"

# Paths a file tool touches, one per line, as given in the hook input ($1 = the input).
# Claude: tool_input.file_path. Codex apply_patch: the patch text in tool_input.command (a string,
# or an array whose elements are joined); its "*** Add/Update/Delete File:" and "*** Move to:" lines.
edited_paths() {
    jq -r '
        [ .tool_input.file_path // empty ]
        + ( (.tool_input.command // "")
            | (if type == "array" then join("\n") elif type == "string" then . else "" end)
            | [ scan("(?m)^\\*\\*\\* (?:Add|Update|Delete) File: (.+)$"), scan("(?m)^\\*\\*\\* Move to: (.+)$") ]
            | map(.[0]) )
        | .[]
    ' <<<"$1"
}

# The shell command of a Bash tool call ($1 = the input). Codex may send an argv array.
bash_command() {
    jq -r '.tool_input.command | if type == "array" then join(" ") else (. // "") end' <<<"$1"
}

# The folder the session works in. Codex patch paths are relative to it, not to the repo root.
input_cwd() {
    local cwd
    cwd="$(jq -r '.cwd // empty' <<<"$1")"
    printf '%s\n' "${cwd:-$repo_root}"
}

# A filesystem-safe session id ($1 = the input).
session_id() {
    jq -r '.session_id // "unknown"' <<<"$1" | tr -c 'a-zA-Z0-9_\n-' '_'
}

# Makes $1 absolute against folder $2 (default: the repo root).
absolute_path() {
    case "$1" in
        /*) printf '%s\n' "$1" ;;
        *) printf '%s\n' "${2:-$repo_root}/$1" ;;
    esac
}
