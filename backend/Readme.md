# BookTracker Backend

REST API for the BookTracker mobile app: Kotlin, Spring Boot, PostgreSQL. Hexagonal architecture.

## Docs

- [Architecture](docs/architecture.md): modules, rules, cross-cutting concerns
- [Book data & catalog](docs/book-data.md): external book sources, merge, rate limits
- [Architecture decision records](docs/adr/README.md)
- [Deployment](docs/deployment.md)
- [MVP features](docs/mvp-features.md)

## Modules

- **`core`:** business logic in pure Kotlin — models, use cases, ports
- **`:adapter:web`:** REST API, DTOs, validation, error handling
- **`:adapter:persistence`:** Spring Data JDBC, Flyway migrations
- **`:adapter:security`:** JWT validation against Keycloak, current user
- **`:adapter:bookdata`:** clients for Google Books, DNB and Open Library
- **`bootstrap`:** the runnable application, wires the modules together
- **`build-logic`:** Gradle convention plugins

## Run

```bash
./gradlew :bootstrap:bootRun
```

Postgres and Keycloak start automatically from `compose.yaml`.

| Service | Port | Purpose |
|---|---|---|
| Backend | 8080 | REST API under `/api/v1`, Swagger UI at `/swagger-ui.html` |
| PostgreSQL | 5432 | App database |
| Keycloak | 8180 | IdP, admin console, realm `booktracker` (imported from `keycloak/realm-booktracker.json`) |

- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

## Deploy

A push to `main` deploys to dev, a tag `v<major>.<minor>.<patch>` on `main` deploys to prod. Details in [docs/deployment.md](docs/deployment.md).
