---
name: run-app
description: Build the BookTracker mobile app, start it on an Android emulator or the iOS simulator, and save a screenshot to look at. Use only when the user asks to run, see, or screenshot the app. Do not use it on your own after a change; the turn-end hook already builds and tests.
---

# Run the app and take a screenshot

Run from the repo root. Each script prints the path of the PNG; open it to check the UI.

| Platform | Command | Arguments |
|---|---|---|
| Android | `tools/run-app/android.sh` | variant (`devDebug` default, `prodDebug`, `devRelease`, `prodRelease`), AVD name |
| iOS | `tools/run-app/ios.sh` | `Dev` (default) or `Prod`, simulator name or UDID |

- Android starts the first AVD if no device is connected. iOS boots the first available iPhone simulator.
- Screenshots land in `app/build/screenshots/` (not in Git).
- To check German strings, switch the device language first: Android `adb shell cmd locale set-app-locales <package> --locales de`; iOS `xcrun simctl spawn <device> defaults write -g AppleLanguages '("de")'`, then restart the app.
- Liquid Glass shows only on iOS 26 or newer. On iOS 17–25 the tab bar is the classic one.

If a script fails because a tool or device is missing (no AVD, no simulator, no `xcodegen`), tell the user what is missing. Do not install SDKs, system images or simulator runtimes yourself.
