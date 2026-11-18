# BookTracker Backend

REST API for the BookTracker mobile app: Kotlin, Spring Boot, PostgreSQL. Hexagonal architecture, details in [docs/architecture.md](docs/architecture.md).

## Modules

- **`core`:** business logic in pure Kotlin — models, use cases, ports
- **`:adapter:web`:** REST API, DTOs, validation, error handling
- **`:adapter:persistence`:** Spring Data JDBC, Flyway migrations
- **`bootstrap`:** the runnable application, wires the modules together
- **`build-logic`:** Gradle convention plugins

## Run

```bash
./gradlew :bootstrap:bootRun
```

Postgres starts automatically from `compose.yaml`.

- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

## Deploy

A push to `main` deploys to dev, a tag `v<major>.<minor>.<patch>` on `main` deploys to prod. Details in [docs/deployment.md](docs/deployment.md).
