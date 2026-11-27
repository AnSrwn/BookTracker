#!/usr/bin/env bash
# Builds an Android variant, installs it on an emulator, starts it and saves a screenshot.
# Starts the first AVD if no device is connected.
#
#   tools/run-app/android.sh [variant] [avd]    variant: devDebug (default), prodDebug, devRelease, prodRelease
#
# Prints the screenshot path. Use the skill run-app; run it only when the user asks.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"
emulator="$sdk/emulator/emulator"
variant="${1:-devDebug}"
avd="${2:-}"

if ! "$adb" get-state >/dev/null 2>&1; then
    [ -n "$avd" ] || avd="$("$emulator" -list-avds | head -1)"
    if [ -z "$avd" ]; then
        echo "No device connected and no AVD found. Create one in the Device Manager of Android Studio." >&2
        exit 1
    fi
    nohup "$emulator" -avd "$avd" -no-snapshot-save >/dev/null 2>&1 &
    "$adb" wait-for-device
    until [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi

capitalized="$(printf '%s' "${variant:0:1}" | tr '[:lower:]' '[:upper:]')${variant:1}"
(cd "$root/app" && ANDROID_HOME="$sdk" ./gradlew --console=plain -q ":android:install$capitalized")

case "$variant" in
    dev*) package="com.sauerwein.booktracker.dev" ;;
    *) package="com.sauerwein.booktracker" ;;
esac
"$adb" shell am force-stop "$package"
"$adb" shell am start -W -n "$package/com.sauerwein.booktracker.android.MainActivity" >/dev/null
sleep 3

out="$root/app/build/screenshots/android-$variant-$(date +%Y%m%d-%H%M%S).png"
mkdir -p "$(dirname "$out")"
"$adb" exec-out screencap -p >"$out"
echo "$out"
