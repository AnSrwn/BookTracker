# ADR-004: Local-first: the local database is the single source for the UI

- **Context:** A guest mode follows directly after the MVP: use the app without an account, keep the books on the device, upload them on sign-up. Later a full sync may follow.
- **Decision:** From the MVP on, the local database (Room) in `shared/data` is the only source the UI reads. Repositories pull backend data into it; writes go to the backend and then into the database. ViewModels observe database flows (`collectIntoState`).
- **Consequences:**
  - ➕ Guest mode later is mostly upload logic, not a rewrite of every feature's data flow.
  - ➕ The bookshelf is readable offline and starts fast.
  - ➖ More work in the MVP: a database, mappers, refresh logic.
  - ⚠ Guest mode also needs backend changes: catalog search without a token (with protection against abuse) and an idempotent upload of private books.
  - ↪ A full sync (offline writes while signed in, several devices) needs a change feed and a conflict rule in the backend.
