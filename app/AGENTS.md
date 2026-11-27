# BookTracker app – rules for coding agents

The repo-wide rules are in the [root `AGENTS.md`](../AGENTS.md). Architecture: [`docs/architecture.md`](docs/architecture.md). Decisions: [`docs/adr/`](docs/adr/README.md).

## Commands

Run Gradle in `app/`.

| What | Command |
|---|---|
| Unit tests, all modules (incl. the Konsist rules) | `./gradlew testAndroidHostTest` |
| Unit tests, one module | `./gradlew :shared:ui:feature:addbook:testAndroidHostTest` |
| iOS compile check | `./gradlew compileKotlinIosSimulatorArm64` |
| Android build | `./gradlew :android:assembleDevDebug` |
| Format / lint | `../tools/ktlint/ktlint.sh -F .` / `../tools/ktlint/ktlint.sh .` (from the repo root: `tools/ktlint/ktlint.sh app`) |
| Xcode project | `cd ios && xcodegen` (the `.xcodeproj` is generated, never edit or commit it) |
| Run and screenshot | skill `run-app`, **only when the user asks** |

The Gradle daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`, D43); Gradle downloads it if it is missing. Do not change that file without asking.

The turn-end hook already runs the tests and compile checks of the modules you changed. Do not run the whole build again by hand unless you need the output.

## Modules

| Module | Put here | Never here |
|---|---|---|
| `shared/core` | Models, use cases, repository interfaces. Pure Kotlin | Compose, Ktor, Room, platform code |
| `shared/data` | Backend API (Ktor), auth, Room database, repository implementations, `AppFlavor`/`AppEnvironment` | UI |
| `shared/ui/navigation` | One `@Serializable` key per screen in `AppNavKey.kt`, tabs | UI, logic |
| `shared/ui/common` | `ScreenViewModel`, `ScreenHost`, `ScreenDestination` | Look, feature code |
| `shared/ui/designsystem` | Theme, shared components, icons | Feature logic |
| `shared/ui/feature/<tab>` | Screens, ViewModels, destinations, strings of one tab | Code of another feature, `data` |
| `shared/app` | Koin start (`app/di/AppKoin.kt`), tab roots, iOS entry point `IosApp` | Feature code |
| `android/` | Activity, Application, app resources | Screens |
| `ios/` | `project.yml`, Swift shell with the native tab bar | Screens |

- Layers: `ui` → `core`, `data` → `core`. Nothing under `shared/ui/` depends on `data`; only `shared/app` knows every module.
- Features never depend on each other. To open a screen of another tab's feature, navigate to its key.
- Konsist tests in `shared/app/src/androidHostTest/.../ArchitectureTest.kt` enforce these import rules. Add a rule there when you add one here.
- ViewModels call use cases from `core`, never `data` directly.
- A new feature module: add it to `settings.gradle.kts`, as a dependency of `shared/app`, and give it `di/<Feature>Module.kt`. Koin finds its module, destinations and ViewModels on its own. Without the `shared/app` dependency iOS never sees it.
- Package = module path: `shared/ui/feature/bookshelf` is `com.sauerwein.booktracker.ui.feature.bookshelf`.

## Screens

Follow [ADR-003](docs/adr/003-screen-pattern.md). `shared/ui/feature/addbook/.../search/` is the reference.

Each screen has its own package `ui.feature.<feature>.<screen>` with one file per part:

| File | Contains | Knows |
|---|---|---|
| `XContract.kt` | `XState` (data class), `XEvent` and `XEffect` (sealed interfaces), nothing else | nothing; screen and ViewModel both depend on it, not on each other |
| `XScreen.kt` | only the `XScreen(state, onEvent)` composable | its contract, never a ViewModel or Koin, so it works in a preview |
| `XViewModel.kt` | only `@KoinViewModel class XViewModel : ScreenViewModel<XState, XEvent, XEffect>` | its contract and use cases from `core` |
| `XDestination.kt` | `@Singleton internal class XDestination : ScreenDestination<XKey>` | the orchestrator: gets the ViewModel with `koinViewModel()`, runs `ScreenHost`, routes events, turns effects into navigation or messages |

Rules:

- `XScreen` takes only `state` and `onEvent` (and `modifier`). Callbacks, snackbar hosts or ViewModels are not parameters; the destination places a snackbar host around the screen if needed.
- Navigation without logic is an event the destination handles itself (`XEvent.YClicked -> navigator.navigate(…)`), without the ViewModel. Navigation or a message after logic or async work is an `Effect`.
- A screen without state or logic has no ViewModel: `XContract.kt` with only `XEvent`, `XScreen(onEvent)` and `XDestination`.
- Koin collects every `@Singleton` destination; there are no lists to maintain.
- Keep `ScreenViewModel` small. No loading, error or retry handling in it; that belongs in use cases or helpers.
- Local-first ([ADR-004](docs/adr/004-local-first.md)): ViewModels read data from database flows (`collectIntoState`), never straight from the API.

## Dependency injection

Koin Annotations with the Koin Compiler Plugin ([ADR-007](docs/adr/007-koin-annotations.md)):

- Put the lifetime on the class: `@Singleton`, `@Factory`, `@KoinViewModel` (from `org.koin.core.annotation`). Inject through the constructor. No `KoinComponent`, no DSL `module { }` blocks.
- Each Gradle module with definitions has exactly one root module, in its own file in the `di` package: `@Module @Configuration @ComponentScan("<module package>") class XModule`. Create it with the module's first definition. All three annotations are required; without them the module is skipped at runtime.
- Types that need a builder (Ktor client, Room database) get a `@Singleton fun` inside the root module.
- Values known only at start come from `koin.declare(…)` in `initKoin` and are received with `@Provided` on the parameter. `AppFlavor` is such a value: never add a definition for it; a consumer marks its parameter `@Provided` (`@Provided` on the class is not seen across Gradle modules).
- Never Koin KSP (`koin-ksp-compiler`, `org.koin.ksp.generated`).
- The build checks the graph: `[KOIN-D001]` / `[KOIN-D003]` means a definition or dependency is missing. Fix the definition, do not work around the check.
- Konsist (`ArchitectureTest`) checks that modules live in `di` packages and carry all three annotations.

## Strings and resources

- Every user-visible text exists in English (`composeResources/values/strings.xml`) and German (`values-de/strings.xml`) in the module that uses it.
- Icons are vector drawables in `shared/ui/designsystem/src/commonMain/composeResources/drawable`.
- iOS tab titles: `ios/BookTracker/Localizable.xcstrings`. Android app name: `android/src/{main,dev}/res/values/app.xml`.

## Platforms

- Minimum iOS 17 and Android 14 (API 34). Liquid Glass shows only on iOS 26+.
- iOS: the native `UITabBarController` in `ios/BookTracker/MainTabBarController.swift` holds one Compose root per tab. Compose dialogs and sheets sit under that tab bar.
- Variants: `devDebug`, `devRelease`, `prodDebug`, `prodRelease`; iOS schemes `BookTracker-Dev` and `BookTracker-Prod` ([ADR-006](docs/adr/006-build-variants.md)).
- Set libraries: Koin (Annotations + Compiler Plugin), Ktor, Room, Coil 3, Navigation 3, Lifecycle, kotlinx.serialization, kotlinx-coroutines-test, Konsist. Ask before adding any other.
