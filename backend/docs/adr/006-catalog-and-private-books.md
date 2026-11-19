# ADR-006: Public Catalog and Private Books in One Table

- **Context:** A book row mixes two things: objective facts about a book (title, author, ISBN, cover) and a user's relationship to it. The target is a central catalog, curated by an admin or imported from public book databases, that every user can see. Next to it are books a user creates when the catalog does not have them, visible only to their creator.
- **Decision:** One `book` table with a **nullable** `owner_id`. `NULL` is a public catalog entry, a set value makes the row private to that user.
  - The visibility rule `owner_id IS NULL OR owner_id = :me` lives behind the `BookRepository` port. No read method may return another user's private row: each one is scoped to the caller or restricted to `owner_id IS NULL`. An unscoped `findAll()` does not exist.
  - Catalog rows have no owner and must not get the curating admin's `sub`.
  - A user's relationship to a book is a separate row: `user_book (user_id, book_id, created_at)`. It links a user to catalog books (shared, never copied) and to their own private books. "My books" (`GET /books`) reads only through it. The visibility rule is right for reading one book or searching, but listing everything visible would show the whole catalog to every user.
  - Reading status, possession and favourite will live on `user_book` as independent fields, not one `status` field, because owning, having read and favouring a book are not mutually exclusive.

| | One `book`, nullable `owner_id` ✅ | `book` + `custom_book` |
|---|---|---|
| Foreign key from the per-user row | one column | polymorphic: two nullable columns plus `CHECK` |
| Reading catalog and private books | one query | merge two sources |
| Publishing a private book | `UPDATE … SET owner_id = NULL` | copy row, rewrite references |
| Visibility enforced by | the port | `NOT NULL` in the schema |

- **Consequences:**
  - ➕ One id space and one mapper. The per-user row needs no polymorphic foreign key.
  - ➕ A private book can become a catalog entry without touching anything that references it.
  - ➖ The port guarantees visibility, not the schema. An unscoped read method would silently leak private books, so none may exist.
  - ➖ "The same ISBN only once" holds for the catalog only: a partial unique index on `isbn` where `owner_id IS NULL AND isbn IS NOT NULL`. Private books are independent entities, and a user may keep several with the same ISBN.
  - ↪ Should private books ever need their own fields, they can move into a separate table. Only the per-user row's foreign key has to follow.
