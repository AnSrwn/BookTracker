# ADR-007: Koin Annotations with the Koin Compiler Plugin

- **Context:** Dependency injection was written in the Koin DSL, with module definitions in the middle of feature code (`val addBookModule = module { … }` next to the destinations) and one hand-written list of modules in `initKoin`. A missing definition showed up only at runtime.
- **Decision:** Koin Annotations, compiled by the Koin Compiler Plugin (`io.insert-koin.compiler.plugin`), with `koin-annotations` from the Koin BOM. Never Koin KSP.

| | Koin DSL (before) | Annotations + KSP | Annotations + Compiler Plugin ✅ |
|---|---|---|---|
| Where definitions live | `module { }` blocks | on the classes | on the classes, modules in `.di` |
| Graph check | at runtime | KSP step, partial | at build time, every `koinViewModel()` call site and the whole graph at `startKoin` |
| Build cost | none | extra KSP step per module | compiler plugin, no extra step |
| Status | supported | being replaced | recommended by Koin |

- **Rules:**
  - Classes carry their lifetime: `@Singleton`, `@Factory`, `@KoinViewModel`. Types that cannot be built from a constructor get a `@Singleton fun` in the module.
  - One root module per Gradle module in a `.di` package (`…ui.feature.addbook.di.AddBookModule`) with `@Module @Configuration @ComponentScan("<own package>")`. A Gradle module without definitions has no module.
  - `shared/app` has `@KoinApplication` and starts Koin with `startKoin<BookTrackerKoinApp>` from common code. The plugin finds every root module; there is no list to maintain.
  - Values known only at start (the build flavor) are declared with `koin.declare(…)` and received with `@Provided` on the parameter. `@Provided` on the class (`AppFlavor`) is not seen by the full-graph check in another Gradle module; tried and reverted (review F26).
  - Navigation stays outside Koin: `ScreenDestination` registers the screens, the destination gets the ViewModel with `koinViewModel()`. Koin only collects the destinations: each is a `@Singleton` class, auto-bound to `ScreenDestination`, and `ScreenDestinations` in `shared/app` receives all of them (D48). Koin's own Navigation 3 integration exists only as DSL and would need a DSL module next to the annotations.
- **Consequences:**
  - ➕ A missing definition or dependency fails the build (`[KOIN-D001]`, `[KOIN-D003]`), also in the turn-end hook.
  - ➕ A new feature is wired by adding its annotated classes and its root module; `shared/app` needs no Koin change.
  - ➕ Konsist checks that modules lie in `.di` packages and that every root module carries all three annotations.
  - ➖ The library modules cannot check the full graph; only `shared/app`, where `startKoin` is, does (expected, the plugin says "validation skipped" there).
  - ⚠ Two mistakes compile and only fail at runtime: a module without `@Configuration` (skipped by discovery) and a destination without `@Singleton` (never collected). Konsist rules catch both; they were verified with deliberate violations.
  - ↪ The start function has no platform hook yet (`androidContext`). It comes with Room in the bookshelf step (review F25).
  - ⚠ Kotlin 2.4.21 is newer than the plugin's verified versions (up to 2.4.20 in 1.2.3); it builds with a warning. Update the plugin when a release lists the Kotlin version.
