# ADR-005: Pure Kotlin Core Containing the Use Cases

- **Context:** The first draft split the core into `domain` (entities, ports, no Spring) and `application` (use cases with `@Service`/`@Transactional`). For a CRUD-heavy app, `domain` would have stayed nearly empty, while the logic worth testing (ownership, scan flow, shelf rules) would have lived in a module with Spring on its classpath.
- **Decision:** One module `core` holds domain models, ports **and** use cases, in pure Kotlin. Use cases are plain classes wired as `@Bean`s in `bootstrap`. Transactions go through a `TransactionRunner` port, implemented with `TransactionTemplate` in `:adapter:persistence`.

| | Single module, feature packages | `domain` + `application` (Spring) | Pure `core` ✅ |
|---|---|---|---|
| Boundaries enforced by | code review only | build | build |
| Where the testable logic lives | mixed with Spring code | module with Spring | Spring-free module |
| Nearly empty module | – | `domain` | – |
| Extra wiring | none | none | one `@Bean` per use case, `TransactionRunner` port |

- **Consequences:**
  - ➕ Use cases and rules are unit-tested in milliseconds with fakes, without Spring context or database.
  - ➕ The whole core is framework-free, enforced by the compiler. No proxy pitfalls (self-invocation, `open` classes) in the core.
  - ➖ One `@Bean` method per use case and an explicit `tx.run { … }` instead of an annotation.
  - ➖ Still more structure than a pure CRUD app strictly needs (mapping code between layers).
  - ↪ If a rich domain emerges later, `core` can be split into `domain` and `application` again.
