# Bootstrap

Assembles all modules into one runnable Spring Boot application (`./gradlew :bootstrap:bootRun`).

## Contains

- **`BookTrackerApplication`:** the main class
- **`UseCaseConfig`:** registers the `core` use cases as Spring beans
- **`application.yaml`:** configuration

## Local secrets

The Google Books key is mandatory while Google is enabled; without it the app refuses to start. Locally it goes into `bootstrap/config/application.yaml`, which is git-ignored and which Spring Boot loads by itself from the working directory (`bootRun` runs in `bootstrap/`; an IDE run configuration must use the same working directory):

```yaml
booktracker:
  bookdata:
    google-books:
      api-key: <your key>
```

To run without Google instead, set `booktracker.bookdata.google-books.enabled: false` there, or `BOOKTRACKER_GOOGLE_BOOKS_ENABLED=false` in the environment. On the servers the key comes from `BOOKTRACKER_GOOGLE_BOOKS_API_KEY`, a stack variable (`deploy/backend/compose.yaml`, see `docs/deployment.md`).
