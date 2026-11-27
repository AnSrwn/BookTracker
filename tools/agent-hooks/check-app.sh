#!/usr/bin/env bash
# Stop hook (Claude and Codex). At the end of a turn it builds and unit-tests the app modules that
# the uncommitted changes touch (D8). On failure it sends the agent back with the error output.
#
# - Runs only if this session changed something under app/ (marked by track-edits.sh, D40).
# - Nothing changed under app/, or nothing changed since the last green run: does nothing.
# - Changes to a base module (core, data, ui/designsystem, ui/common, ui/navigation) or to the build:
#   all modules. A feature: that feature, shared/app and android. android: only android.
# - Per Kotlin module: Android host unit tests (incl. the Konsist rules in shared/app) and the iOS
#   simulator compile.
# - ios/, the iOS entry point (shared/app/src/iosMain) or the build: also generates the Xcode project
#   and builds the dev scheme for the simulator. Without xcodegen that part is skipped and reported.
# - The same failed state blocks only once; after 3 failed checks in a row the turn may end.
# - Runs of parallel sessions wait for each other and keep separate logs.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

input="$(cat)"
session="$(session_id "$input")"
app="$repo_root/app"
mkdir -p "$state_dir"
touched_file="$state_dir/touched-$session"
blocks_file="$state_dir/blocks-$session"
failed_file="$state_dir/last-failed-$session"
green_file="$state_dir/last-green"
log="$state_dir/check-$session.log"

[ -f "$touched_file" ] || exit 0

changed="$(git -C "$repo_root" status --porcelain=v1 -uall -- app | sed -E 's/^...//; s/.* -> //')"
if [ -z "$changed" ]; then
    rm -f "$touched_file"
    exit 0
fi

fingerprint="$(
    cd "$repo_root" || exit 1
    git diff HEAD -- app
    git ls-files --others --exclude-standard -z -- app | xargs -0 shasum 2>/dev/null
)"
fingerprint="$(shasum <<<"$fingerprint" | cut -d' ' -f1)"
if [ -f "$green_file" ] && [ "$(cat "$green_file")" = "$fingerprint" ]; then
    rm -f "$touched_file" "$blocks_file" "$failed_file"
    exit 0
fi
if [ -f "$failed_file" ] && [ "$(cat "$failed_file")" = "$fingerprint" ]; then
    jq -n '{systemMessage: "Turn-end check: the app still fails as before (nothing changed since). See app/build/agent-hooks/."}'
    exit 0
fi

all=false
ios=false
modules=()
add_module() {
    local m
    for m in "${modules[@]+"${modules[@]}"}"; do [ "$m" = "$1" ] && return; done
    modules+=("$1")
}
while IFS= read -r path; do
    rel="${path#app/}"
    case "$rel" in
        shared/core/* | shared/data/* | shared/ui/designsystem/* | shared/ui/common/* | shared/ui/navigation/*) all=true ;;
        build-logic/* | gradle/* | *.gradle.kts | gradle.properties)
            all=true
            ios=true
            ;;
        shared/ui/feature/*)
            feature="$(cut -d/ -f4 <<<"$rel")"
            add_module ":shared:ui:feature:$feature"
            add_module ":shared:app"
            add_module ":android"
            ;;
        shared/app/*)
            add_module ":shared:app"
            add_module ":android"
            case "$rel" in shared/app/src/iosMain/*) ios=true ;; esac
            ;;
        android/*) add_module ":android" ;;
        ios/*) ios=true ;;
    esac
done <<<"$changed"

tasks=()
if $all; then
    tasks=(testAndroidHostTest compileKotlinIosSimulatorArm64 :android:compileDevDebugKotlin)
else
    for m in "${modules[@]+"${modules[@]}"}"; do
        if [ "$m" = ":android" ]; then
            tasks+=(":android:compileDevDebugKotlin")
        else
            tasks+=("$m:testAndroidHostTest" "$m:compileKotlinIosSimulatorArm64")
        fi
    done
fi

# One Gradle or Xcode run at a time across sessions. Wait well below the hook timeout (600 s); if
# another session still runs its check then, skip this one and leave its lock alone (review F18).
# A lock whose owner process is gone is taken over.
lock="$state_dir/check.lock"
got_lock=false
for _ in $(seq 1 300); do
    if mkdir "$lock" 2>/dev/null; then
        echo $$ >"$lock/pid"
        got_lock=true
        break
    fi
    owner="$(cat "$lock/pid" 2>/dev/null)"
    # Re-read the owner right before removing, so a lock another waiter has just taken stays
    if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null && [ "$(cat "$lock/pid" 2>/dev/null)" = "$owner" ]; then
        rm -rf "$lock"
        continue
    fi
    sleep 1
done
if ! $got_lock; then
    jq -n '{systemMessage: "Turn-end check skipped: another session is still checking the app. It runs again at the end of your next turn."}'
    exit 0
fi
trap 'rm -rf "$lock"' EXIT

: >"$log"
failed=""
notice=""
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

if [ ${#tasks[@]} -gt 0 ]; then
    if ! (cd "$app" && ./gradlew --console=plain -q "${tasks[@]}") >>"$log" 2>&1; then
        failed="Gradle (${tasks[*]})"
    fi
fi

if $ios && [ -z "$failed" ]; then
    if ! command -v xcodegen >/dev/null; then
        notice="The iOS project was not checked: xcodegen is missing (brew install xcodegen)."
    elif ! (cd "$app/ios" && xcodegen --quiet &&
        xcodebuild -project BookTracker.xcodeproj -scheme BookTracker-Dev -configuration Dev-Debug \
            -destination "generic/platform=iOS Simulator" -derivedDataPath "$app/build/ios-derived" \
            -quiet CODE_SIGNING_ALLOWED=NO build) >>"$log" 2>&1; then
        failed="Xcode build (BookTracker-Dev)"
    fi
fi

if [ -z "$failed" ]; then
    rm -f "$blocks_file" "$failed_file" "$touched_file"
    if [ -n "$notice" ]; then
        # Not green: the iOS part has to be checked once xcodegen is there
        jq -n --arg msg "Turn-end check: Kotlin is green. $notice" '{systemMessage: $msg}'
    else
        echo "$fingerprint" >"$green_file"
    fi
    exit 0
fi

echo "$fingerprint" >"$failed_file"
blocks=$(($(cat "$blocks_file" 2>/dev/null || echo 0) + 1))
if [ "$blocks" -gt 3 ]; then
    rm -f "$blocks_file"
    jq -n --arg msg "Turn-end check still fails after 3 attempts: $failed. See ${log#"$repo_root"/}." \
        '{systemMessage: $msg}'
    exit 0
fi
echo "$blocks" >"$blocks_file"

jq -n --arg reason "Turn-end check failed: $failed. Fix it before you finish. Last lines of the output:
$(grep -v '^w: ' "$log" | tail -60)" '{decision: "block", reason: $reason}'
exit 0
