#!/usr/bin/env bash
# Generates the Xcode project, builds the app for the simulator, installs and starts it,
# and saves a screenshot. Boots the given simulator, or the first available iPhone.
#
#   tools/run-app/ios.sh [Dev|Prod] [simulator name or UDID]
#
# Prints the screenshot path. Use the skill run-app; run it only when the user asks.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
flavor="${1:-Dev}"
device="${2:-}"
derived="$root/app/build/ios-derived"

command -v xcodegen >/dev/null || { echo "xcodegen is missing: brew install xcodegen" >&2; exit 1; }
(cd "$root/app/ios" && xcodegen --quiet)
xcodebuild -project "$root/app/ios/BookTracker.xcodeproj" -scheme "BookTracker-$flavor" \
    -configuration "$flavor-Debug" -destination "generic/platform=iOS Simulator" \
    -derivedDataPath "$derived" -quiet CODE_SIGNING_ALLOWED=NO build

if [ -z "$device" ]; then
    device="$(xcrun simctl list devices available -j |
        jq -r '[.devices[][] | select(.name | startswith("iPhone"))] | (map(select(.state == "Booted")) + .)[0].udid // empty')"
fi
if [ -z "$device" ]; then
    echo "No iPhone simulator found. Add one in Xcode: Window > Devices and Simulators." >&2
    exit 1
fi
xcrun simctl boot "$device" 2>/dev/null || true
xcrun simctl bootstatus "$device" -b >/dev/null

case "$flavor" in
    Dev) bundle="com.sauerwein.booktracker.dev" ;;
    *) bundle="com.sauerwein.booktracker" ;;
esac
xcrun simctl install "$device" "$derived/Build/Products/$flavor-Debug-iphonesimulator/BookTracker.app"
xcrun simctl terminate "$device" "$bundle" 2>/dev/null || true
xcrun simctl launch "$device" "$bundle" >/dev/null
sleep 4

out="$root/app/build/screenshots/ios-$flavor-$(date +%Y%m%d-%H%M%S).png"
mkdir -p "$(dirname "$out")"
xcrun simctl io "$device" screenshot "$out" >/dev/null
echo "$out"
