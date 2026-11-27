# ADR-002: Modules split into core, data and ui, plus one module per feature

- **Context:** Agents write most of the code. Module boundaries enforced by Gradle keep the layers apart and keep each change small. iOS should link exactly one Kotlin framework: several frameworks each bring their own Kotlin runtime, and their types do not match.
- **Decision:** `shared/` has three layers, `core`, `data` and `ui`, and one umbrella module `app`. Everything with a UI lives under `shared/ui/`.

| Module | Content | Depends on |
|---|---|---|
| `shared/core` | Domain: models, use cases, repository interfaces. Pure Kotlin | – |
| `shared/data` | Backend API, auth, local database, repository implementations | `core` |
| `shared/ui/navigation` | Navigation 3 keys and tabs, no UI | – |
| `shared/ui/common` | `ScreenViewModel`, `ScreenHost`, `ScreenDestination` | `ui/navigation` |
| `shared/ui/designsystem` | Theme, components, icons | – |
| `shared/ui/feature/*` | Screens and ViewModels of one tab | `core`, `ui/navigation`, `ui/common`, `ui/designsystem` |
| `shared/app` | DI, tab roots, the iOS framework `Shared` | all |
| `android` | Android app: Activity, Application, resources | `shared/app` |
| `ios` (not Gradle) | Xcode project, native tab bar | framework `Shared` |

Rule: `ui` → `core`, `data` → `core`. `ui` never sees `data`; only `app` knows every module and wires `data` into `ui` through Koin.

- **Consequences:**
  - ➕ The folder tells the layer: everything under `shared/ui/` is UI, `core` and `data` are not.
  - ➕ `core` cannot use Compose, Ktor or Room: the convention plugin does not add them.
  - ➕ The turn-end hook only builds and tests the modules a change touches.
  - ➖ More build files. Convention plugins in `build-logic` keep them short.
  - ➕ Konsist tests (`ArchitectureTest` in `shared/app`, run with the host unit tests) fail when a feature imports another feature, `ui` imports `data`, or `core` imports another layer.
  - ⚠ Gradle alone only stops what a module does not declare; Konsist catches the imports, not an unused declared dependency.
  - ⚠ Every new shared module must be added to `shared/app`, or iOS never sees it.
