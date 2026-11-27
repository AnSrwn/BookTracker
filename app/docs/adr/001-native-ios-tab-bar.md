# ADR-001: Compose Multiplatform with a native iOS tab bar

- **Context:** The app runs on Android and iOS and shares its UI. On iOS 26+ the tab bar should use Liquid Glass. Compose draws on its own canvas and cannot render Liquid Glass; only native UIKit/SwiftUI bars get it from the system.
- **Decision:** Screens, the navigation inside a tab (Navigation 3) and the top bars are Compose, shared by both platforms. On iOS a native `UITabBarController` holds one Compose view controller per tab. On Android a Material 3 navigation bar holds the same tab contents.

| | Everything in Compose | Native tab bar ✅ | Full native shell (TabView + NavigationStack) |
|---|---|---|---|
| Liquid Glass | none, at most an imitation | tab bar | tab bar, navigation bars, toolbars |
| Navigation logic | once, Kotlin | once, Kotlin | twice, Swift and Kotlin |
| Swift code | none | a tab bar controller | navigation and bars |

- **Consequences:**
  - ➕ One navigation logic for both platforms, little Swift, real glass where it shows most.
  - ➕ iOS 17–25 shows the classic tab bar with the same code.
  - ➖ Top bars are Compose, so no glass there.
  - ➖ Compose dialogs and bottom sheets sit under the native tab bar: the scrim does not cover it and it stays tappable.
  - ➖ Each tab is its own Compose root. Switching tabs from Kotlin needs a bridge to Swift, added with the first feature that needs it.
  - ↪ A fully native shell stays possible later: the screens do not change, only the shell around them.
