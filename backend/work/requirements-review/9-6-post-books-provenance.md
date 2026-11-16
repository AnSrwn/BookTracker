# 9.6 Saving a book — clarification record (user library via `user_book`)

> Started 2026-11-16 · Status: **implemented** on branch `feature/user_books` (not committed). Unit tests green; migrations and the end-to-end run as two Keycloak users checked on the dev stack. Q22 is deferred to 9.7.

## Goal

A user's library holds the books they added. A catalog book is **linked**; nobody gets a private copy of a catalog book just for having it. A book the user wants to change, or enters by hand, is a **private book**, linked the same way. Every book has its own id, and it is always clear whether a book is a catalog book or a private book.

## Scope: two steps (D10)

**Step A, new, before 9.6: user library.**
- `user_book` table: links a user to a catalog book or to one of their own private books.
- Migration: every existing private book gets its link; legacy catalog rows stay unlinked (D13).
- `GET /books` lists the user's library through `user_book` (fixes CB2).
- `POST /books` (create private book) also writes the link.
- Drop `ux_book_owner_isbn` (D14b).
- Library responses show the book's kind (D20).

**Step B, 9.6 reduced: add catalog books.**
- New endpoint: add a catalog book to the library by id (D17).
- Scan and search results carry the book id and kind (D20).
- Search hits with ISBN, title and author are upserted into the catalog (D19, D22).

**Out of scope:** removing books from the library (D24, MVP feature, no endpoint yet), editing private books (`PUT`), reading status, current page, shelves (later on `user_book`), Google Books / DNB / the composite source (9.7–9.9), the review workflow.

## Sources

| ID | Location | Note |
|----|----------|------|
| S1 | `docs/roadmap.md:228-233` (§9.6) | Original 9.6: save with provenance. The file is git-ignored (local notes) |
| S2 | `docs/architecture.md:445` | Save takes corrected fields + optional provenance |
| S3 | `docs/architecture.md:436` | Every resolved and every saved record is upserted; search hits never are |
| S4 | `docs/architecture.md:619-622` (ADR-006) | One `book` table, `owner_id NULL` = catalog. Plans "a separate per-user row referencing the book" |
| S5 | `docs/mvp-features.md` | Scan fields correctable before saving; reading progress; shelves; "remove books from the library" |
| S6 | `docs/roadmap.md:80-91, 225` (§6, §9.5) | V3 left existing rows `NULL` → catalog rows; ISBN unique per owner; "search hits are never upserted" |
| S7 | Forked session `local_120bb284…`, 2026-11-16 | The user's position on the link table (D9) and the fork's analysis |
| C1 | `GetBooksUseCase.kt`, `BookQueries.kt:9,22` | `GET /books` = `findAllVisible` = own private rows + **every** approved catalog row |
| C2 | `ResolveIsbnUseCase.kt`, `SearchCatalogUseCase.kt` | The scan upserts; search does not |
| C3 | `V4__book_source.sql` | `ux_book_catalog_isbn`, `ux_book_owner_isbn` |
| C4 | `BookCandidateResponse.kt`, `BookCandidate.kt`, `OpenLibraryClient.kt:17` | No id in scan/search results; the search requests no ISBN field |
| R-1 | Live check, Open Library `search.json`, 2026-11-16 | With `fields=…,editions,editions.key,editions.isbn` each hit carries **one matching edition** with its own key and ISBNs (`eragon` → `OL22650690M`, `9780375840548`). That edition can differ from `cover_edition_key`, and for one hit it was a different language edition (*Brisingr* work → Russian *Ėragon*) |
| R-2 | Dev DB, 2026-11-16 | Flyway at V3 (V4 not applied yet); 2 rows, both catalog (`owner_id NULL`) with ISBN; no private rows |

## Current behavior (read from the code, not run)

- CB1 `POST /books` stores a private row (`manual`); no link, no catalog write.
- CB2 **Bug:** `GET /books` lists every approved catalog row for every user. Since 9.5 every scan upserts one, so anyone's scan appears in everyone's library.
- CB3 The scan upserts the catalog row before any save; search hits are never upserted.
- CB4 Scan and search results carry no book id and no kind.
- CB5 "Having" a book today means "owning a private row".

## Decisions

All by the user on 2026-11-16.

- D9 (S7): a **link table** connects users to private and catalog books. No private copy per user for catalog books.
- D10 (Q11): two steps, A before B (see Scope).
- D12 (Q14): a user may have the catalog *Eragon* and a private *Eragon* at the same time.
- D13 (Q16): legacy rows from before V3 stay **unlinked catalog rows**; the dev library starts empty.
- D14 (Q18):
  - (a) Adding a catalog book that is already in the user's library → **409**.
  - (b) Private books are independent entities. Changing one is an edit of that book. Creating another with the same ISBN is **allowed**, so `ux_book_owner_isbn` goes. Catalog ISBN uniqueness stays.
- D15 (Q19): **catalog books are never edited from the frontend.** Only private books are created and edited.
- D16 (Q20): **no field comparison** on the server. The request says what it is: "add catalog book X" or "create a private book".
- D17 (Q15): **two endpoints.** `POST /books` creates a private book (fields, as today). A separate endpoint adds a catalog book by id; its path is a planning detail.
- D19 (Q21) — **superseded by D27**: search hits are upserted into the catalog too, so every result the app can add has an id and the app never decides which catalog book to save. Reverses 9.5 (X10).
- D20 (Q21): **every book has its own id**, independent of ISBN or source, and **the kind (catalog or private) is always visible**. In the DB this already holds (UUID `id`, `owner_id`). The API shows the id and the kind in scan and search results and in library responses.
- D21 (A4): "create my own version" of a catalog book is **a frontend process**: the app copies the catalog data into the form, the user edits, and the app calls `POST /books`. No extra endpoint.
- D22 (Q23): a search hit enters the catalog only with **ISBN, title and at least one author**. Page count is not required. Hits without them have no id (→ Q22).
- D23 (A3): `GET /books/{id}` keeps today's visibility rule: **any catalog book** or the user's own private book, whether or not it is in their library. `GET /books` lists only the library.
- D25 (planning, user, 2026-11-16): API naming puts the kind in the path. `POST /books/private` creates a private book (replaces `POST /books`), and `POST /books/catalog/{id}` adds a catalog book. `GET /books` and `GET /books/{id}` stay.
- D26 (planning, user, 2026-11-16): a new branch `feature/user_books` from the latest `main`, covering only this scope.
- D24 (Q24): after "create my own version" the **backend only creates the private book**. The app removes the catalog entry via "remove from library" once that exists; until then both stay (D12).

## Code review of PR #10 (2026-11-16)

All 8 findings checked against the code and valid. Decisions taken with the user while discussing findings 4 and 5:

- D27 (finding 4, user): **save on add, not on search.** Search only reads; a hit is stored as a catalog book only when a user adds it. Supersedes D19. Scans still store immediately. Reason: a GET that writes lets retries, prefetching or a script grow the shared catalog, and thin search rows overwrote full rows (finding 1) and blocked full scans (finding 2).
- D28 (user): **signed opaque id for hits not stored yet.** HMAC over source + record id; the app sends whatever id it got (UUID or hit id) to `POST /books/catalog/{id}` and never learns the provider. No server state. Signed, not encrypted: opaque, not secret.
- D29 (user): **fetch the full record on add** via a general port method (`findByRecordId`); never store thin search data. If the source is down, the add fails with 503.

| # | Finding | Handling |
|---|---------|----------|
| 1 | Search overwrites full catalog rows | resolved by D27 |
| 2 | Thin search rows block full ISBN lookups | resolved by D27/D29 |
| 3 | Search hit mixes edition and work year/cover | year, cover, publisher from the edition |
| 4 | GET writes to the shared catalog | resolved by D27 |
| 5 | One transaction per stored hit | user wanted it fixed; gone with D27 (search no longer writes) |
| 6 | Library order without tie-breaker | `ORDER BY ub.created_at DESC, book.id` |
| 7 | `Location` from cutting up the path | `API_PREFIX` + context path |
| 8 | Conflict message from a `when` on the code | code and message passed together |

Verified 2026-11-16: 127 unit tests green; manual run as `testuser` — search `eldest` returned UUIDs for stored and `h1.` ids for addable hits and wrote no row (13 → 13); adding a hit stored the full edition (*Eldest*, Knopf, 679 pages) and linked it (14 rows); again → 409; tampered or garbage id → 404; scan still returns a UUID.

## Second code review of PR #10 (2026-11-16, after c7713e2)

The first review's 8 findings were confirmed fixed. Both new findings are valid:

| # | Finding | Handling |
|---|---------|----------|
| 1 | Adding a hit could link a user to a non-approved catalog row: the upsert's `DO UPDATE` ignored `review_state`, overwrote the row and returned it | `DO UPDATE … WHERE book.review_state = 'APPROVED'`; no row returned → `upsertCatalogEntry` returns `null` → add is 404, scan returns the candidate without id. **Also affected the scan path**, which the review did not mention |
| 2 | `BookCandidateResponse` still exposed `source`/`sourceId`, contradicting D28 | both fields removed. **The 503 detail also named the provider** ("Book source open_library is unavailable"), which the review did not mention; now a fixed text, the source stays in the log |

Open question, deferred to the review workflow (roadmap §11): `findLibrary` has no review filter, so a linked book stays in a user's books if its row is later rejected.

Verified 2026-11-16: 131 unit tests green; SQL by hand in a rolled-back transaction (a `PENDING` row is neither returned nor changed by the upsert); manual run as `testuser` — search and scan responses carry no `source`/`sourceId`, adding a hit (*Brisingr*, 800 pages) still gives 201.

## Requirements

| ID | Requirement | Step | Source |
|----|-------------|------|--------|
| R10 | `GET /books` returns exactly the books linked to the caller in `user_book`: no other user's links, no unlinked catalog books | A | D9, CB2 |
| R12 | `POST /books` creates a private book (`manual`, owner = caller) from the sent fields and links it to the caller | A | D9, D17 |
| R18 | A user may have several private books with the same ISBN | A | D14b |
| R19 | Migration: every existing private book gets a link to its owner; catalog rows get none | A | D13 |
| R20 | `GET /books/{id}` answers for any approved catalog book and for the caller's own private books; another user's private book is 404 | A | D23, ADR-006 |
| R21 | Library and book responses show the book's id and whether it is a catalog or a private book | A | D20 |
| R11 | Adding a catalog book by id creates a link only, with no new `book` row | B | D9, D17 |
| R13 | Adding a catalog book never creates or changes a `book` row | B | D15 |
| R14 | Only approved catalog books can be added. An unknown id, another user's private book or the caller's own private book → 404 | B | ADR-006, D17 |
| R17 | Adding a catalog book that is already in the caller's library → 409 | B | D14a |
| R16 | The library may hold a catalog book and a private book with the same ISBN | A/B | D12 |
| R22 | Scan and search results carry an opaque id and kind for every result the app may add: a stored book's UUID, or a signed hit id for a hit not stored yet | B | D20, D28 |
| R23 | ~~Search upserts every eligible hit with an ISBN~~ (superseded by D27): search never writes; adding a hit (eligible, with ISBN, title, author) fetches and stores the full record | B | D22, D27, D29 |
| R24 | A search hit is edition-level: its `sourceId` and ISBN come from the same edition | B | D22, R-1 |

## Planning notes (technical, no business decision)

- N1 Open Library search needs `editions`, `editions.key`, `editions.isbn` (and probably `editions.title`) in `fields`. The hit's `sourceId` and ISBN come from that edition (R24), not from `cover_edition_key` plus the work's ISBN list, which mixes editions. New fixtures are needed.
- N2 The matching edition can be another language edition (R-1). Use the edition's own title for the catalog row, so the row stays internally consistent.
- N3 The upsert goes by `(source, sourceId)`. An ISBN collision with another catalog row is swallowed as in 9.5, and the result shows the existing row's id. Up to 20 writes per search; Open Library's policy concerns requests, not storage.
- N4 `BookCandidate` gets an optional id + kind; `Book.toCandidate()` passes them through.
- N5 `findAllVisible` loses its only caller (`GetBooksUseCase`); remove it if nothing else uses it.
- N6 Dev DB is at V3 (R-2). V4 and the new migration run on the next app start; check them by hand there.

## Clarification points

| ID | Question | Status |
|----|----------|--------|
| Q11 | Own step before 9.6? | answered → D10 |
| Q12 | Who decides "edited"? | answered → D11, superseded by D16 |
| Q13 | Changing a linked catalog book | answered → D18, refined by D21/D24 |
| Q14 | Catalog + private *Eragon* at once? | answered → D12 |
| Q15 | One or two endpoints? | answered → D17 |
| Q16 | Legacy rows from before V3 | answered → D13 |
| Q17 | Which id does the library API use? | answered → D20, D23: the book's own id; `user_book` stays internal |
| Q18 | Same kind twice | answered → D14 |
| Q19 | What counts as "edited"? | dropped: no comparison (D16) |
| Q20 | Is the comparison still needed? | answered → D16 |
| Q21 | How does the app get the catalog id? | answered → D19, D20 |
| Q22 | Results that cannot enter the catalog (no ISBN/author per D22; Google Books hits from 9.7, blocked by the allowlist) have no id, so the app can only create a private book from them | **deliberately deferred** to 9.7. Today: Open Library hits without an edition ISBN. They are still shown, without id |
| Q23 | Thin catalog rows | answered → D22 |
| Q24 | Who removes the catalog entry after "own version"? | answered → D24 |
| Q9, Q10 | Pair-check edge cases | dropped: no pair is sent any more |

## Deviations from the sources (docs to update after implementation)

- X5 S1/S2: 9.6 "save with provenance, checked on the server" becomes "add catalog books by id". Rewrite roadmap §9.6 and `architecture.md:445`.
- X6 S3: "every one a user saves is upserted" no longer holds; the scan and search upsert, saving never does.
- X7 S6 (§6): "having a book" was "owning a private row", and step 6's "Done when" ("a row with `owner_id IS NULL` appears for both") describes CB2. Right for visibility, wrong for the library.
- X8 §5/§6 + V4: "the same ISBN only once per owner" (`ux_book_owner_isbn`, `DuplicateBook` for private books) is dropped.
- X9 S5: "can be corrected before saving" now means creating a private book prefilled from the scan (D21).
- X10 S6 (§9.5) + `architecture.md:436`: "search hits are never upserted" is reversed, limited to hits with ISBN, title and author (D19, D22).

## Acceptance examples

| ID | Given | When | Then | Req |
|----|-------|------|------|-----|
| E10 | A scanned *Eragon* (catalog row exists); B never added it | B calls `GET /books` | *Eragon* is not in B's list | R10 |
| E11 | Catalog *Eragon* with id X | A adds X | 201; one `user_book` A → X; still one `book` row | R11, R13 |
| E12 | A has added X | A adds X again | 409 | R17 |
| E13 | — | A creates a private book (fields) | 201; private row (`manual`, owner A) + link; response shows kind private | R12, R21 |
| E14 | A has a private *Eragon* (ISBN Y) | A creates another private book with ISBN Y | 201; two private *Eragon*s in A's library | R18 |
| E15 | A has catalog *Eragon* X | A creates a private *Eragon* with the same ISBN | 201; library shows both | R16, D24 |
| E16 | B has a private book P | A adds P via the catalog endpoint; A calls `GET /books/P` | 404; 404 | R14, R20 |
| E17 | Catalog *Eragon* X not in A's library | A calls `GET /books/X` | 200, kind catalog | R20, R21 |
| E18 | Open Library hit with edition `OL22650690M`, ISBN 9780375840548, title and author | A searches `eragon` | Hit is upserted; result carries its id and kind catalog | R22, R23, R24 |
| E19 | Open Library hit whose edition has no ISBN | A searches | Result shown without id; no catalog row | R23, Q22 |
| E20 | Dev DB with 2 legacy catalog rows and private rows of user U | Migration runs | U's private rows are linked to U; the legacy rows are linked to nobody | R19, D13 |
| E21 | A scans an ISBN (catalog-first or resolved) | Scan response | Carries the catalog id and kind catalog | R22 |

## Implementation status (2026-11-16)

| Req | Result |
|-----|--------|
| R10, R12, R18, R16 | `GetBooksUseCaseTest`, `CreatePrivateBookUseCaseTest` green |
| R11, R13, R14, R17 | `AddCatalogBookUseCaseTest` green |
| R20 | `GetBookUseCaseTest` green |
| R22, R23, R24 | `SearchCatalogUseCaseTest`, `FindBookByIsbnUseCaseTest`, `UpsertCatalogEntryUseCaseTest`, `OpenLibraryMapperTest` green |
| R19 (E20) | Dev DB: V4 + V5 applied, 0 links for the 2 legacy catalog rows, `ux_book_owner_isbn` gone |
| R21 | Checked at runtime: `kind` in book, scan and search responses |
| End to end (two users) | Checked 2026-11-16 as `testuser`/`testuser2`: E10, E11, E12, E14, E15, E16, E17, E18, E21 as expected; one catalog row per ISBN afterwards |

## Verification plan

| Req | Example | Check |
|-----|---------|-------|
| R10 | E10 | `GetBooksUseCaseTest` with an in-memory `user_book` store: two users, one catalog book |
| R11, R13, R17 | E11, E12 | Use-case test for "add catalog book": link count, `book` rows unchanged, 409 |
| R14, R20 | E16, E17 | Use-case tests: 404 for a foreign private book (add and get); 200 for an unlinked catalog book |
| R12, R18, R16 | E13–E15 | `AddBookUseCaseTest`: private row + link; second private with the same ISBN accepted |
| R21, R22 | E13, E17, E18, E21 | Web mapper unit tests: id + kind in the responses |
| R23, R24 | E18, E19 | `OpenLibraryMapperTest` against new search fixtures with `editions`; `SearchCatalogUseCaseTest`: search never writes; `AddCatalogBookUseCaseTest`: full record on add |
| R19 | E20 | Manual: migration on the dev DB, then `SELECT` on `user_book` |
| all | — | Manual run against the dev stack: scan → add, search → add, create private, `GET /books` as two dev users |

Unit tests only (no slice/Testcontainers tests), per project convention.

## Superseded (model before D9, kept for traceability)

- D1 422 for a pair that does not check out · D2 save despite a source outage · D3 check the pair through the ISBN · D5 the app drops the pair when the ISBN is edited · D6 a pair without an ISBN is ignored · D8 the private row is always `manual` · D11 the server compares the sent fields — **superseded** by D9/D16.
- D4 the catalog row is built from source data only, corrections stay private — still holds (R13, D15).
- D7 responses show no `source`/`sourceId` — still holds; D20 adds the kind, not the source.
- A1 a save calls no source — still holds.
- D18 "own version replaces the link" — refined by D21 and D24: the backend only creates the private book.
