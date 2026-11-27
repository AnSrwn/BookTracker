#!/usr/bin/env bash
# Self-test of guard.sh with Claude- and Codex-shaped hook input. Run it after changing guard.sh:
#   tools/agent-hooks/test-guard.sh
# Each case says whether the guard must allow or deny the tool call.
set -uo pipefail

root="$(git rev-parse --show-toplevel)"
guard="$root/tools/agent-hooks/guard.sh"
nl=$'\n'
pass=0
fail=0

check() { # name, expected (allow|deny), hook input
    local out result
    out="$(printf '%s' "$3" | "$guard")"
    if grep -q '"deny"' <<<"$out"; then result=deny; else result=allow; fi
    if [ "$result" = "$2" ]; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        echo "FAIL: $1 → $result (expected $2)"
    fi
}

bash_case() { # name, expected, command [, cwd]
    check "$1" "$2" "$(jq -n --arg c "$3" --arg cwd "${4:-$root}" '{tool_name: "Bash", cwd: $cwd, tool_input: {command: $c}}')"
}

file_case() { # name, expected, tool, path
    check "$1" "$2" "$(jq -n --arg t "$3" --arg p "$4" '{tool_name: $t, tool_input: {file_path: $p}}')"
}

patch_case() { # name, expected, patch path, cwd
    check "$1" "$2" "$(jq -n --arg p "$3" --arg cwd "$4" \
        '{tool_name: "apply_patch", cwd: $cwd, tool_input: {command: ("*** Begin Patch\n*** Update File: " + $p + "\n*** End Patch")}}')"
}

# Protected files through file tools (Claude) and patches (Codex, relative to cwd)
file_case "Edit local.properties" deny Edit "$root/app/local.properties"
file_case "Write .env" deny Write "$root/backend/.env"
file_case "Write .env.example" allow Write "$root/backend/.env.example"
file_case "Edit gradle wrapper" deny Edit "$root/app/gradle/wrapper/gradle-wrapper.properties"
file_case "Edit Kotlin file" allow Edit "$root/app/shared/core/src/commonMain/kotlin/X.kt"
file_case "Edit .env outside the repo" allow Edit "/tmp/x/.env"
patch_case "Codex patch on wrapper, cwd app" deny "gradle/wrapper/gradle-wrapper.properties" "$root/app"
patch_case "Codex patch on Kotlin, cwd app" allow "shared/core/x.kt" "$root/app"

# Shell writes to protected files
bash_case "echo >> .env" deny 'echo KEY=1 >> backend/.env'
bash_case 'echo > "local.properties" (quoted)' deny 'echo x > "app/local.properties"'
bash_case "rm 'app/gradlew' (quoted)" deny "rm 'app/gradlew'"
bash_case "printf > local.properties" deny 'printf "sdk.dir=/x" > app/local.properties'
bash_case "heredoc into local.properties" deny "cat > app/local.properties <<EOF${nl}sdk.dir=/x${nl}EOF"
bash_case "sed -E -i local.properties" deny 'sed -E -i "" s/a/b/ app/local.properties'
bash_case "git checkout -- gradlew" deny 'git checkout -- app/gradlew'
bash_case "cp .env.example .env" deny 'cp .env.example .env'
bash_case "rm gradlew" deny 'rm app/gradlew'

# Shell commands that only read or touch other files
bash_case "cat .env" allow 'cat backend/.env'
bash_case "cp local.properties /tmp" allow 'cp app/local.properties /tmp/bak'
bash_case "sed -i .env.example" allow 'sed -i "" s/a/b/ backend/.env.example'
bash_case "rm ~/.gradle/wrapper" allow 'rm -rf ~/.gradle/wrapper/dists'
bash_case "./gradlew test > out.txt" allow 'cd app && ./gradlew test > out.txt 2>&1'
bash_case "./gradlew build | tee log" allow './gradlew build | tee build.log'
bash_case "path with -m-like part" allow 'ls some-module/src'

# Commit and push: plain forms pass (the clients ask), disguised forms are denied (D11)
bash_case "plain commit" allow 'git commit -m "Add x"'
bash_case "plain commit, unquoted message" allow 'git commit -m fix'
bash_case "commit message with -> and gradlew" allow 'git commit -m "Map a -> b, gradlew wrapper docs"'
bash_case "commit -am message with rm" allow 'git commit -am "rm app/local.properties"'
bash_case "cd && git push" allow 'cd /x && git push -u origin feature/x'
bash_case "commit on its own line" allow "git status${nl}git commit -m x"
bash_case "commit after then" allow 'if git diff --quiet; then git commit -m x; fi'
bash_case "git -C status" allow 'git -C /x status'
bash_case "git log" allow 'git log --oneline -3'
bash_case 'grep "git commit"' allow 'grep -rn "git commit" AGENTS.md'
bash_case "rg 'git push'" allow "rg 'git push' docs"
bash_case "PR body mentions git push" allow 'gh pr create --title "Fix" --body "Do not git push to main"'
bash_case "heredoc mentions git push" allow "cat > /tmp/n.md <<EOF${nl}Then run git push.${nl}EOF"
bash_case "git -C commit" deny 'git -C /x commit -m y'
bash_case 'git -C "/a b" commit' deny 'git -C "/tmp/a b" commit -m x'
bash_case "git -c push" deny 'git -c core.hooksPath=/dev/null push'
bash_case "git --no-pager commit" deny 'git --no-pager commit -m x'
bash_case "env prefix commit" deny 'GIT_EDITOR=true git commit -m x'
bash_case "env prefix push" deny 'HUSKY=0 git push'
bash_case "path to git" deny '/usr/bin/git commit -m x'
bash_case 'sh -c "git -C app commit"' deny 'sh -c "git -C app commit"'
bash_case "bash -lc plain commit" deny "bash -lc 'git commit -m x'"
bash_case "then git -C commit" deny 'if true; then git -C /x commit -m y; fi'

echo "guard.sh: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
