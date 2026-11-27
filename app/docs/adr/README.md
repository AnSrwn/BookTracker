# Architecture Decision Records (app)

| ADR | Decision |
|---|---|
| [001](001-native-ios-tab-bar.md) | Compose Multiplatform with a native iOS tab bar |
| [002](002-module-layout.md) | Modules split into core, data and ui, plus one module per feature |
| [003](003-screen-pattern.md) | One screen pattern: State, Events, Effects |
| [004](004-local-first.md) | Local-first: the local database is the single source for the UI |
| [005](005-own-oidc-code.md) | Own code for the Keycloak login instead of an OIDC library |
| [006](006-build-variants.md) | Four build variants, dev app next to the prod app |
| [007](007-koin-annotations.md) | Koin Annotations with the Koin Compiler Plugin |

Same format as the [backend ADRs](../../../backend/docs/adr/README.md): Context, Decision and Consequences (➕ benefit, ➖ cost, ↪ way out later, ⚠ warning).
