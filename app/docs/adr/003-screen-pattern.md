# ADR-003: One screen pattern: State, Events, Effects

- **Context:** Every screen should look the same, so that agents copy one pattern instead of inventing one per screen.
- **Decision:** Unidirectional data flow, built on Jetpack ViewModel, Navigation 3 and Koin, without an extra framework:
  - `ScreenViewModel<State, Event, Effect>`: one immutable `State`, events in through `onEvent`, one-off `Effect`s out through a channel.
  - `ScreenHost`: collects the state with the lifecycle and the effects only while the screen is STARTED, then calls the stateless screen with `state` and `onEvent`.
  - `ScreenDestination`: one per screen, the orchestrator around a Navigation 3 entry. It gets the ViewModel with `koinViewModel()`, handles navigation events and effects, and forwards the other events.
  - Rule: navigation without logic is an event the destination handles itself, without the ViewModel. Navigation or a message after logic or async work is an effect.
  - A screen without state or logic has no ViewModel.
  - One package per screen with four files (D49, D52): `XContract.kt` (`XState`, `XEvent`, `XEffect`), `XScreen.kt` (only the stateless composable `XScreen(state, onEvent)`), `XViewModel.kt` (only the ViewModel), `XDestination.kt` (the orchestrator: `koinViewModel()`, `ScreenHost`, event routing, effects, D51). Screen and ViewModel depend on the contract, not on each other, so the screen works in a preview.
  - Destinations are `@Singleton` classes; Koin collects them for `shared/app` (D48).
  - Reference: `ui/feature/addbook/search/`. `AddBookViewModel` sends `ShowSearchNotAvailable` after its check; `AddBookDestination` shows it in a snackbar and handles `AddManuallyClicked` as navigation.

| | Now in Android style | This pattern ✅ | Orbit MVI | Circuit |
|---|---|---|---|---|
| One-off events | as state, confirmed by an event | effects (channel) | side effects | events in the presenter |
| Extra library | – | – | Orbit | Circuit, own navigation |
| Base class | – | small `ScreenViewModel` | `ContainerHost` | – |

- **Consequences:**
  - ➕ Unit tests are simple: send an event, check the state or the effect.
  - ➕ The stateless content works in previews.
  - ➖ An effect is delivered only while the screen is STARTED; until then it waits in the channel. A ViewModel that is cleared before that drops it.
  - ⚠ `ScreenViewModel` stays small. Loading, errors or retries go into use cases or helpers, not into the base class.
