# ADR-006: Four build variants, dev app next to the prod app

- **Context:** The backend runs as dev and prod, each with its own Keycloak realm. Testing against dev must not touch prod data.
- **Decision:** Four variants on both platforms: `devDebug`, `devRelease`, `prodDebug`, `prodRelease` (Android flavors × build types; iOS configurations `Dev-Debug` … and schemes `BookTracker-Dev`, `BookTracker-Prod`).

| | dev app | prod app |
|---|---|---|
| App id | `com.sauerwein.booktracker.dev` | `com.sauerwein.booktracker` |
| Name | BookTracker Dev | BookTracker |
| Backend at start | `api.dev.booktracker.sauerwein.me`, realm `booktracker-dev` | `api.booktracker.sauerwein.me`, realm `booktracker` |
| Login redirect | `booktracker-dev://auth/callback` | `booktracker://auth/callback` |
| Dev menu | yes (auth step): switch dev ↔ prod | no |

- **Consequences:**
  - ➕ Dev and prod app install side by side.
  - ➕ The host names are in the code (`AppEnvironment`); the repo is public, and they are public anyway.
  - ➖ The dev scheme must be allowed in both realms, since the dev app can switch to prod.
  - ⚠ Switching the server signs out and clears the local database, so dev and prod data never mix.
  - ⚠ Release builds are signed with the debug key until the release step.
