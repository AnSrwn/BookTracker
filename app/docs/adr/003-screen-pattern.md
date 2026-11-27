# ADR-003: One screen pattern: State, Events, Effects

- **Context:** Every screen should look the same, so that agents copy one pattern instead of inventing one per screen.
- **Decision:** Unidirectional data flow, built on Jetpack ViewModel, Navigation 3 and Koin, without an extra framework:
  - `ScreenViewModel<State, Event, Effect>`: one immutable `State`, events in through `onEvent`, one-off `Effect`s out through a channel.
  - `ScreenHost`: collects the state with the lifecycle and the effects only while the screen is STARTED, then calls a stateless `Content(state, onEvent)`.
  - `ScreenDestination`: one per screen, a thin wrapper around a Navigation 3 entry. It gets the ViewModel with `koinViewModel()` and turns callbacks and effects into navigation.
  - Rule: navigation without logic is a callback from the screen. Navigation or a message after logic or async work is an effect.
  - A screen without state or logic has no ViewModel.
  - Reference: `AddBookViewModel` sends `ShowSearchNotAvailable` after its check, `AddBookScreen` shows it as a snackbar.

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
