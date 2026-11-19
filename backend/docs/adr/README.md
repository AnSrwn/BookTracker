# Architecture Decision Records

| ADR | Decision |
|---|---|
| [001](001-hexagonal-architecture.md) | Hexagonal architecture with one Gradle module per adapter |
| [002](002-spring-data-jdbc.md) | Spring Data JDBC with Flyway for persistence |
| [003](003-keycloak.md) | Self-hosted Keycloak, the backend is a pure resource server |
| [004](004-image-handling.md) | Covers as hotlinked URLs, no own image storage in the MVP |
| [005](005-pure-kotlin-core.md) | One pure Kotlin `core` with models, ports and use cases |
| [006](006-catalog-and-private-books.md) | Public catalog and private books in one table |
| [007](007-book-data-from-public-sources.md) | Book data from public sources, stored in a self-growing catalog |
| [008](008-edition-as-catalog-unit.md) | The edition is the unit of the catalog |

Each record has Context, Decision and Consequences (➕ benefit, ➖ cost, ↪ way out later, ⚠ warning).
