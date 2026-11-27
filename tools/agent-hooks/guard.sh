#!/usr/bin/env bash
# PreToolUse hook (Claude: Edit|Write|MultiEdit|Bash, Codex: apply_patch|Edit|Write|Bash).
# - Blocks changes to secrets and machine files (D8): .env files, local.properties, the Gradle
#   wrapper of this repo. The agent should ask the user instead.
# - Blocks `git` with global options before `commit`/`push` (`git -C dir commit`): the approval
#   prompts of both clients (D11) match only plain `git commit` / `git push`.
# The shell checks catch obvious writes only; they are no sandbox.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

input="$(cat)"
cwd="$(input_cwd "$input")"

deny() {
    jq -n --arg reason "$1" '{
        hookSpecificOutput: {
            hookEventName: "PreToolUse",
            permissionDecision: "deny",
            permissionDecisionReason: $reason
        }
    }'
    exit 0
}

# $1 = absolute path
protected_path() {
    case "$1" in "$repo_root"/*) ;; *) return 1 ;; esac
    case "$(basename "$1")" in
        .env.example | .env.sample) return 1 ;;
        .env | .env.* | local.properties | gradlew | gradlew.bat) return 0 ;;
    esac
    case "$1" in */gradle/wrapper/*) return 0 ;; esac
    return 1
}

while IFS= read -r path; do
    [ -n "$path" ] || continue
    path="$(absolute_path "$path" "$cwd")"
    if protected_path "$path"; then
        deny "${path#"$repo_root"/} is protected (secrets, local machine settings or the Gradle wrapper). Ask the user to change it."
    fi
done < <(edited_paths "$input")

[ "$(jq -r '.tool_name // ""' <<<"$input")" = "Bash" ] || exit 0
command="$(bash_command "$input")"

# Drop text that is no command: commit messages (-m, -am, --message) and heredoc bodies. The
# heredoc's first line stays, so `cat > app/local.properties <<EOF` is still checked (review F22)
command="$(perl -0777 -pe '
    s/(?<![^\s;&|(])(?:-[A-Za-z]*m|--message)(?:=|\s+)(?:"(?:[^"\\]|\\.)*"|\x27[^\x27]*\x27|[^\s"\x27]+)//g;
    s/<<-?[ \t]*(["\x27]?)(\w+)\1([^\n]*)\n(?:.*?\n)?[ \t]*\2[ \t]*(?=\n|$)/<<$2$3/gs;
' <<<"$command")"

# `git commit` / `git push` must be plain where a command starts (start, ; && || | ( $( ` newline,
# then, do, else, !). Env assignments, a path to git, options before the subcommand or a nested
# shell (`sh -c "git … commit"`) skip the approval prompts (D11). Quoted text only mentions the
# words (grep "git commit", PR bodies) and is not checked.
if perl -e '
    my $c = $ARGV[0];
    my $q = q{(?:"(?:[^"\\\\]|\\\\.)*"|\x27[^\x27]*\x27)};
    while ($c =~ /\b(?:ba|z)?sh\s+-\w*c\s+($q)/g) {
        exit 0 if $1 =~ /\bgit\b.*\b(?:commit|push)\b/s;
    }
    $c =~ s/$q/Q/g;
    my $start = q{(?:^|[;&|(`\n]|\$\(|\b(?:then|do|else)\b|!)};
    while ($c =~ /$start[ \t]*((?:[A-Za-z_]\w*=\S*[ \t]+)*)(\S*\/)?git((?:[ \t]+(?:-[Cc][ \t]+\S+|--[\w-]+(?:=\S+)?|-\w+))*)[ \t]+(?:commit|push)\b/g) {
        exit 0 if $1 ne "" || defined($2) || $3 ne "";
    }
    exit 1;
' "$command"; then
    deny "Run plain \`git commit\` or \`git push\` (cd into the repo first, no env variables, path or options before the subcommand), so the user is asked for approval."
fi

# Leave out what cannot write a protected repo file: the Gradle cache in the home folder and the
# .env templates. Quote characters are dropped, quoted paths stay (review F16)
scan="$(sed -E "s/[\"']//g; s#(~|\\\$HOME|\\\$\\{HOME\\})/\\.gradle[^[:space:]]*##g; s/\\.env\\.(example|sample)//g" <<<"$command")"
target='(\.env([.][A-Za-z0-9_-]+)?([[:space:];|&)]|$)|local\.properties|gradle/wrapper/|gradlew(\.bat)?([[:space:];|&)]|$))'
# Writers whose arguments are all written or removed
writer='((^|[^-])>>?|\btee\b|\bsed([[:space:]]+-[A-Za-z]+)*[[:space:]]+-[A-Za-z]*i|\bperl([[:space:]]+-[A-Za-z]+)*[[:space:]]+-[A-Za-z]*i|\brm\b|\bmv\b|\btruncate\b|\bchmod\b|\bgit[[:space:]]+(checkout|restore)\b)'
if grep -Eq "$writer[^;&|]*$target" <<<"$scan"; then
    deny "This command writes to a protected file (secrets, local.properties or the Gradle wrapper). Ask the user to do it."
fi
# cp writes only its last argument
while IFS= read -r copy; do
    destination="$(awk '{ print $NF }' <<<"$copy")"
    if grep -Eq "$target" <<<"$destination "; then
        deny "This command overwrites a protected file (secrets, local.properties or the Gradle wrapper). Ask the user to do it."
    fi
done < <(grep -Eo '\bcp\b[^;&|]*' <<<"$scan")

exit 0
