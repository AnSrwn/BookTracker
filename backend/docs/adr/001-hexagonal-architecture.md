# ADR-001: Hexagonal Architecture with Gradle Modules

- **Context:** Package conventions alone erode over time. Boundaries should be enforced technically.
- **Decision:** Ports & adapters: a pure core plus one Gradle module per adapter, packages per feature within the modules. [ADR-005](005-pure-kotlin-core.md) refines the shape of the core.
- **Consequences:**
  - ➕ Boundary violations are compile errors. `core` stays testable without Spring.
  - ➕ Technologies (DB, auth) are replaceable within their adapter.
  - ➖ More mapping code (DTO ↔ domain ↔ entity) and more build configuration.
  - ➖ A single feature is spread across several modules.
