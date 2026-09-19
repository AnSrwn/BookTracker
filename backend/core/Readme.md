# Core

All business logic, in pure Kotlin: no Spring, no database, no HTTP. Organized by feature (`core.book`, …).

## Contains

- **Domain models:** `Book`, `BookId`, …
- **Use cases:** one class per user action, e.g. `AddBookUseCase`
- **Ports:** interfaces the adapters implement, e.g. `BookRepository`, `CurrentUserProvider`, `TransactionRunner`
