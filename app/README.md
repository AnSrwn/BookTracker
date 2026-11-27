# BookTracker App

The mobile app for Android and iOS, built with Compose Multiplatform. Architecture in [docs/architecture.md](docs/architecture.md), decisions in [docs/adr](docs/adr/README.md).

So far it is a skeleton: three tabs with placeholder screens, the module layout, the screen pattern and four build variants. The features follow step by step.

## Prerequisites

- JDK 25 for the Gradle daemon, pinned in `gradle/gradle-daemon-jvm.properties`. If no JDK 25 is installed, Gradle downloads one from foojay on the first build. On Apple silicon prefer an arm64 JDK; an x86 JDK runs under Rosetta and makes Kotlin/Native builds slow.
- Android SDK with platform 37. Gradle finds it through `ANDROID_HOME` or `sdk.dir` in `local.properties`.
- Xcode 26 or newer, selected with `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`.

## Build and run

All commands run in `app/`.

| What | Command |
|---|---|
| Unit tests | `./gradlew testAndroidHostTest` |
| Android, dev app | `./gradlew :android:installDevDebug` |
| All Android variants | `./gradlew assembleDevDebug assembleDevRelease assembleProdDebug assembleProdRelease` |
| Generate the Xcode project | `cd ios && xcodegen` |
| iOS, dev app | open `ios/BookTracker.xcodeproj`, scheme `BookTracker-Dev` |
| Format / lint Kotlin | `../tools/ktlint/ktlint.sh -F .` / `../tools/ktlint/ktlint.sh .` |

## Variants

| | dev | prod |
|---|---|---|
| Android | `devDebug`, `devRelease` | `prodDebug`, `prodRelease` |
| iOS scheme | `BookTracker-Dev` | `BookTracker-Prod` |
| App id | `com.sauerwein.booktracker.dev` | `com.sauerwein.booktracker` |
| Backend | `api.dev.booktracker.sauerwein.me` | `api.booktracker.sauerwein.me` |
