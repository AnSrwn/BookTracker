# 9.7 Google Books and the composite source: clarification record

> Started 2026-11-16 · Status: **implemented** on branch `feature/google_books` (not committed). All points answered (D1–D10); A1, A3, A4 confirmed with the plan. 191 unit tests green; manual run checked (see Verification results).

## Goal

Add Google Books as a second provider, used **only for free-text search**, and replace the single injected source with a `CompositeBookMetadataSource`. The composite chooses providers from their descriptors and configuration, never by name. If Google fails or runs out of daily budget, search still answers from the CC0 sources.

## Scope

**In 9.7 (roadmap S1):**
- `GoogleBooksClient`: free-text search only (`supportsIsbnLookup = false`), API key, `country=DE`, retry `503`, never retry `429`/quota-`403`.
- Daily-budget guard: on `429`/quota-`403`, Google drops out of the fan-out.
- Key validation at startup.
- `BookDataProperties`: one uniform block per provider. `@ConditionalOnProperty` per client.
- `CompositeBookMetadataSource` over `List<BookMetadataSource>`, descriptor-driven.
- The item deferred from 9.6 (9.6/Q22): what happens to Google hits, which may not enter the catalog (→ Q1).

**Not in 9.7:** DNB (9.8). Field merge, dedupe, grouping by work and parallel fan-out on virtual threads (9.9). The local daily counter and the per-environment keys (§10 hardening). Caching (§11).

## Sources

| ID | Location | Note |
|----|----------|------|
| S1 | `docs/roadmap.md` §9.7 (git-ignored, local) | Build items and "Done when" for 9.7 |
| S2 | `docs/architecture.md` §7 *External Book Data* | Search order Google first, DNB never. Descriptors, config block, `@ConditionalOnProperty`. Rate limits: `429`/quota-`403` = budget gone, `503` retry, key mandatory, `country=DE` |
| S3 | `docs/architecture.md` ADR-007 | "**Google Books finds the book; DNB and Open Library describe the one we keep.**" Google records are not persisted |
| S4 | `docs/architecture.md` ADR-008 | Edition is the unit; non-print manifestations are filtered |
| S5 | `work/requirements-review/9-6-post-books-provenance.md` | Q22 deferred to 9.7. D22 (add needs ISBN), D27 (search only reads), D28 (signed hit ids), D29 (add fetches the full record) |
| S6 | `docs/roadmap.md` §10 *External Book Data* | Local daily counter (e.g. 50/day in dev), separate key per environment: hardening, not 9.7 |
| S7 | Google APIs Terms of Service, `developers.google.com/terms`, §5(e) and §8(b), read 2026-11-16 | Unless the content owner or the law expressly permits it, you may not "scrape, build databases, or otherwise create permanent copies of such content" or keep cached copies longer than the cache header allows. API-specific terms win in a conflict; the Books API's own terms have not been checked. ADR-007 calls persisting Google records "legally grey"; §5(e) is more explicit than that |
| C1 | `core/.../BookMetadataSource.kt` | Port: `descriptor`, `search`, `findByIsbn`, `findByRecordId` |
| C2 | `core/.../usecases/AddCatalogBookUseCase.kt:50-58` | `storeFromSource` compares `ref.source` with **the one** injected source's descriptor id |
| C3 | `core/.../usecases/SearchCatalogUseCase.kt` | Local first. External hits appended when there are fewer than `min-local-hits`. A source failure throws → 503 for the whole search, even when local hits exist |
| C4 | `adapter/web/.../CatalogWebMapper.kt` | Hit id only for `isAddable` hits (`persistableInPublicCatalog && sourceId && isbn`) |
| C5 | `adapter/bookdata/.../OpenLibraryConfig.kt` | Base URL, timeouts and rate hard-coded. No properties class, no `@ConditionalOnProperty` |
| C6 | `adapter/bookdata/src/test/resources/fixtures/google-books-isbn.json` | The only Google fixture: an `isbn:` query with `totalItems: 0`. **There is no free-text fixture** |
| R1 | Workspace, 2026-11-16 | `.env.example` has no Google entry. No `.env`, and no key in the shell environment |
| R2 | Live probe without a key, 2026-11-16 | `eragon`, `intitle:eragon` and three `isbn:` queries, all with `country=DE`: every call `429` "Queries per day" from the anonymous shared pool. Confirms S2 (key mandatory). It says nothing about issue `569300353` |

| R3 | Live probe with the key, 2026-11-16, `country=DE`, 13 calls | See the status table below. Every call `200` on the first try |

## Google Books API status (known problems, re-checked for 9.7)

`architecture.md` §7 (incl. ADR-007) and roadmap §9.7 were updated with these findings on 2026-11-16, at the user's request. The outage is kept as history there; `supportsIsbnLookup` stays `false` pending Q6.

| Problem | First seen | Last checked | Result |
|---------|------------|--------------|--------|
| Field-restricted operators (`intitle:`, `inauthor:`, `isbn:`) return `totalItems: 0` while plain queries work. Google issue `569300353` | 2026-10-03 | **2026-11-16** (R3) | **fixed.** `intitle:eragon` → 125 (was 0), `inauthor:paolini` → 127, `isbn:0735619670` → exactly 1, *Code Complete* (the documented control). Plain `eragon` → 399 |
| `ISBN:` in uppercase silently becomes fuzzy free text | 2026-10 | 2026-11-16 | **still a trap.** `ISBN:0735619670` → 10 unrelated hits (top: *Programming in Python 3*), 0 of 5 carry the ISBN |
| Anonymous requests share an exhausted pool | 2026-10 | 2026-11-16 (R2) | still `429` "Queries per day"; key mandatory |
| Frequent `503 Service temporarily unavailable` (5 of 8 calls in one run) | 2026-10 | 2026-11-16 | none in 13 calls. Too few calls to drop the retry |
| `403` "Cannot determine user location" without `country` from non-geolocatable IPs | 2026-10 | — | not re-checked (needs a container); `country=DE` stays |

New facts from R3, relevant if Google ever does ISBN lookup (→ Q6):

- G1 **`isbn:` finds the right book, but the hit does not always carry the ISBN.** `isbn:0735619670` and `isbn:9780735619678` both return *Code Complete*, but as a library-scan record whose only identifier is `OTHER:UCSC:…`. The ISBN cannot be verified from the response.
- G2 **One ISBN can return several volumes of the same edition.** `isbn:9780375826689` → 2 hits: a library scan (552 pages, no ISBN in the record) and a publisher record (ISBN_10 + ISBN_13, `pageCount: 0`). `0` has to be read as "unknown", not as zero pages.
- G3 ISBN-10 and ISBN-13 forms of the same query return the same volume.
- G4 **Coverage claim in `architecture.md` §7 is out of date.** It says only DNB has `9783570402931` (cbj, 2014). Google now returns it: *Eragon 01. Das Vermächtnis der Drachenreiter*, 2014-08-26, 733 pages, ISBN_10 + ISBN_13, no publisher. The October lookup ran while `isbn:` was broken, so "Google has no record" was probably a symptom of the bug, not a coverage gap.

**Not changed by the fix:** Google records still may not enter the public catalog (S3), and an ISBN lookup still spends one of 1,000 daily calls.

## Current behavior (read from the code, not run)

- CB1 Exactly one `BookMetadataSource` bean (Open Library, wrapped in `ResilientBookMetadataSource`) is injected into all three use cases.
- CB2 Search: if Open Library fails, the whole request is a 503, including when local hits exist (C3).
- CB3 Adding a hit only works when the hit id's source is the injected source's id (C2). A composite with its own id would break every add. → T1
- CB4 Open Library settings are constants in code, not configuration (C5).

## Technical findings (for planning, not product questions)

- T1 The composite needs to route `findByRecordId` to the provider named in the hit id. `AddCatalogBookUseCase` must not compare against a single descriptor. Either the port gets a source parameter, or the composite routes by source id. Resolved in planning.
- T2 The free-text Google fixture is missing (C6, R1). Capturing it needs your key for one live call during implementation. Without the key, the client can only be tested against a hand-written fixture.
- T3 Google's daily quota resets at midnight Pacific time. The guard's "until when" follows from that. Proposal: skip Google until the next midnight PT after a daily-quota `429`/`403`. Any other `429` gets a short cooldown. Implementation detail, recorded here.
- T4 Until 9.9, the composite concatenates search hits in a configured search order (Google first, per S2). It does not merge. The field-precedence lists only get a reader in 9.9, so 9.7 does not configure them.

## Decisions

All by the user on 2026-11-16.

- D1 (Q1): **Google finds, CC0 describes.** A Google hit with an ISBN gets a signed hit id. Adding it resolves the ISBN the way a scan does: catalog first, then the CC0 sources. The result is stored and linked. If no CC0 source knows the ISBN → 404 (what the app does then: Q10, see D6). Accepted effects: the stored book can differ from the hit the user saw, and until 9.8 many German editions do not resolve. A Google hit without an ISBN has no id (→ Q9). Closes 9.6/Q22.
- D2 (Q2): **search answers with what it has, and an outage without results is an error.** Deviates from the suggestion (a) and changes CB2.
  - Local hits exist → `200` with the local hits plus whatever the external sources returned, even if every external source failed.
  - No local hits, and the external sources answered without hits → `200` with an empty list ("no such book").
  - No local hits, and the external sources failed (API down, timeout, other errors) → an error status (`503`, as today).
  - The mixed case, some sources miss and others fail → D4.
- D4 (Q7): **an outage is not a miss.** No local hits, no external hits, and at least one asked source failed → `503`. A provider skipped because its daily budget is spent does **not** count as failed: it was not asked. Otherwise every search miss would be a `503` until the budget resets.
- D5 (Q4): **the same rule for ISBN lookup.** Not found anywhere, but at least one capable source failed → `503`. `404` only when every asked source answered cleanly. Matters from 9.8; in 9.7 only Open Library does lookup, so behavior stays as today.
- D6 (Q8, user): **Google only helps find a book. Google data is never stored**, neither in the catalog nor in a private book. It is shown in search results at request time and nothing else. Reason: Google's API terms (S7 §5(e)) forbid permanent copies, and this is a legal question the project does not want to argue. Consequences:
  - The app must not offer "copy into a private book" for a Google hit, and must not pre-fill any form from one (→ Q9, Q10).
  - Adding a Google hit stores only the CC0 record behind its ISBN (D1 already works this way). On a 404 the app does **not** fall back to a private book pre-filled from the hit (corrects D1's "the app offers a private book" → Q10).
  - Typing a book in by hand stays the user's own entry; the backend cannot and need not tell where the user read it.
  - `architecture.md` §7 and ADR-007 still say Google data "can only ever become a private book". To be corrected in 9.7.
- D7 (Q6, consequence of D6): **Google does not take part in ISBN lookup.** Its only benefit was pre-filling a private book, which D6 rules out. `supportsIsbnLookup` stays `false`; the scan path spends no Google budget.
- D8 (Q9): **Google hits without an ISBN are dropped** from search results. Every Google hit the app sees has an id.
- D9 (Q10): when adding a Google hit returns 404, the app opens an **empty** manual form. Nothing from the hit is carried over, not even the ISBN. (App-side rule; the backend's part is D6/D8.)
- D10 (Q5): **simple ISBN dedupe in 9.7**, ahead of 9.9's merge. A hit is dropped when an earlier result already has the same ISBN-13. "Earlier" follows the result order: local hits, then providers in the configured search order (Google first). The first one wins as it is; no field merge.
  - Interpretation (A4, to confirm with the plan): only **catalog** results suppress later hits. The caller's own private book with ISBN `X` does not hide an addable hit for `X`, since D12 of 9.6 allows a catalog and a private copy side by side.
- D11 (code review finding 1, user, 2026-11-16): **a miss needs someone to have looked.** Nothing found and no provider answered cleanly — every asked one skipped for a spent quota — is a 503, like a failure. Refines D4: a skipped provider still does not count as failed while another one answered cleanly (E8e unchanged). With no provider running at all, a miss stays a miss (catalog-only setup). Same rule for ISBN lookup.
- D12 (code review finding 4, user, 2026-11-16): **one retry** on Google's `503` (2 attempts) instead of 3, until 9.9 asks the providers in parallel. Accepted effect: at October's 503 rate Google would be missing from about a quarter of searches instead of about an eighth.
- D3 (Q3): **startup fails** when Google is enabled and the key is empty, with a clear message. Running without Google needs `enabled: false`. `enabled` defaults to `true`.

## Assumptions (to be confirmed)

- A1 The local daily counter (S6) stays in §10 and is not part of 9.7. In 9.7 the guard only reacts to Google's own `429`/`403`.
- A2 ~~Google hits appear in search results with Google's data~~ → confirmed by D6.
- A3 Google volumes with `printType = MAGAZINE` are filtered out, consistent with ADR-008.

## Open points

| ID | Point | Impact | Status |
|----|-------|--------|--------|
| Q1 | Can the user add a Google hit (was 9.6/Q22)? | Whether most of Google's recall leads to a catalog book or only to a private copy | **answered** → D1 |
| Q2 | Search when every external source fails | 503 vs partial answer; changes CB2 | **answered** → D2 (mixed case split off as Q7) |
| Q3 | Google enabled but no key at startup | Startup fails vs runs without Google | **answered** → D3 |
| Q7 | Search with no local hits, where some sources answered with no hits and others failed (from D2). Also: does a Google provider skipped for exhausted budget count as failed? | 503 vs empty list. If a skipped Google counts as failed, every search miss after the budget runs out is a 503 for the rest of the day | **answered** → D4 |
| Q4 | ISBN lookup with several capable providers, where one fails and the others miss: 404 or 503? | Scan flow goes to the manual form for a book that might exist. Only matters from 9.8 (in 9.7 only Open Library does ISBN lookup) | **answered** → D5 |
| Q5 | Duplicates in search before 9.9 (the same edition from Google and from Open Library) | Visible duplicate rows in dev until 9.9 | **answered** → D10 (deviates from the proposal) |
| Q9 | Google hits without an ISBN: they cannot be added (no id) and, after D6, must not be copied either | Shown but useless, and the app cannot tell them from copyable CC0 hits without an id unless the response says so | **answered** → D8 |
| Q10 | Adding a Google hit returns 404 (no CC0 source knows the ISBN). What does the app open? | Empty manual form vs. form with only the ISBN pre-filled | **answered** → D9 |
| Q8 | A private book pre-filled from Google data (a hit without id copied into the form; with Q6 also a scan) is stored permanently in our DB. Is that a "permanent copy" under S7 §5(e), or the user's own entry? | Affects the existing copy-to-private flow for Google hits and the value of Q6. Not a technical question; nobody has checked the Books API's own terms | **answered** → D6 |
| Q6 | `isbn:` works again (R3). Should Google take part in ISBN lookup? The roadmap said "flip `supportsIsbnLookup` once it is fixed", while architecture §7 says the decision stands regardless of the fix | Scan of a book no CC0 source knows: empty manual form vs. a pre-filled private book. Costs Google budget on the scan path, but only on a CC0 miss if Google is asked last | **answered** → D7 (follows from D6) |

### Q1: Can a Google hit be added?

Case: the user searches `eragon das vermächtnis`. Google returns the cbj edition with ISBN `9783570402931`. Open Library does not have it.

- **(a) No id.** The hit can only be copied into a private book (the deferred 9.6 state). This is simple, but much of Google's recall, its main purpose, never becomes a catalog book.
- **(b) "Google finds, CC0 describes" (S3).** A Google hit **with an ISBN** gets a signed hit id for that ISBN. On add, the backend resolves the ISBN the way a scan does: catalog first, then the CC0 sources. It stores and links the result. If no CC0 source knows the ISBN → 404, and the app offers a private book. Effect: the stored book may differ from the hit the user saw (different title spelling, cover, page count). Until DNB (9.8), many German editions will not resolve.
- **(c) Like (b), but resolved at search time** so the result is known before the user taps. This costs one CC0 lookup per Google hit at 1 req/s, so 10 hits take about 10 s. Not viable.

**Suggestion: (b).** It is the division of labour the architecture already describes, and the add flow already exists for scans.

### Q2: Search when every external source fails

Case: fewer than 5 local hits. Google is out of budget and Open Library times out.

- **(a) 503,** as today, even when there are 1–4 local hits. The app shows "try again"; the user never mistakes a partial list for a complete one.
- **(b) Return the local hits; 503 only when there are none.** The user sees something, but cannot tell that the list is incomplete. The response has no flag for that.
- **(c) Always 200**, with an empty list at worst. An outage then looks like "no such book".

**Suggestion: (a).** It keeps "an outage is not a miss" (S2), and losing a *single* source still degrades quietly, as the roadmap asks.

### Q3: Google enabled but no API key

Case: someone starts the backend without `BOOKTRACKER_GOOGLE_BOOKS_API_KEY`, and Google is enabled.

- **(a) Startup fails** with a clear message. Running without Google then needs an explicit `enabled: false`.
- **(b) Startup succeeds without Google** and logs a warning. This is convenient, but a missing key in production goes unnoticed except as worse search results.

**Suggestion: (a),** with `enabled` defaulting to `true` in `application.yaml`. A missing key is a configuration error, and the roadmap's reason for validating the key is that silent degradation looks like a quota problem.

## Acceptance examples (draft, refined once Q1–Q3 are decided)

| ID | Initial state | Event | Expected | Refs |
|----|---------------|-------|----------|------|
| E1 | Google and Open Library enabled, fewer than 5 local hits | search `eragon` | Google hits first, then Open Library hits | S1, S2, T4 |
| E2 | Google answers `429` "Queries per day" | search | Open Library hits only; Google not called again until the reset; metric shows it | S1, T3 |
| E3 | Google answers `503` twice, then 200 | search | Google hits included; 3 calls in total | S1 |
| E4 | Google answers `429` | search | exactly 1 Google call, no retry | S1 |
| E5 | `google_books.enabled: false` | start + search | no Google bean, no Google call, no code change | S1 |
| E6 | any | scan an ISBN | Google is never called | S1, S2 |
| E7 | Google hit with ISBN `X`, Open Library knows `X` | add the hit | the Open Library edition is stored (or the existing catalog row reused) and linked; `201` | D1 |
| E7b | Google hit with ISBN `X`, no CC0 source knows `X` | add the hit | `404`; no catalog row; no form pre-filled from the hit (detail: Q10) | D1, D6 |
| E7c | Google hit without an ISBN | search | not in the results | D8 |
| E7e | Google and Open Library both return ISBN `X` | search | one result for `X`, the Google one (search order) | D10 |
| E7f | approved catalog book with ISBN `X` locally, Google returns `X` | search | only the local catalog book (UUID) | D10 |
| E7g | the caller's private book with ISBN `X`, Google returns `X` | search | both shown (A4) | D10, A4 |
| E7d | any Google hit | any request | no `book` row (catalog or private) ever holds data taken from a Google response | D6 |
| E8 | every external source fails, 2 local hits | search | `200` with the 2 local hits | D2 |
| E8b | no local hits, every external source answers with no hits | search | `200`, empty list | D2 |
| E8c | no local hits, every external source fails | search | `503` | D2 |
| E8d | no local hits, Google failed, Open Library answers with no hits | search | `503` | D4 |
| E8e | no local hits, Google skipped (budget spent), Open Library answers with no hits | search | `200`, empty list | D4 |
| E9 | Google enabled, key empty | start | startup fails with a message naming the variable | D3 |
| E9b | Google `enabled: false`, key empty | start | starts, no Google bean | D3 |

## Verification plan

- Unit: `GoogleBooksMapper` against a captured free-text fixture (T2). Retry/no-retry and budget guard against a mocked HTTP layer (E2–E4).
- Unit: composite with fake providers. Descriptor-based skipping (E6), search order (E1), one failing provider degrades (E2), the outage rules (E8–E8e), budget-skipped provider not counted as failed (E8e).
- Unit: `SearchCatalogUseCase`: ISBN dedupe (E7e–E7g), Google hits without ISBN dropped (E7c).
- Unit: `AddCatalogBookUseCase`: Google hit id resolves via ISBN to the CC0 record (E7), 404 without a row (E7b). Against `InMemoryBookRepository.stored()`: no row ever has `source = google_books` (E7d); the DB allowlist is the backstop.
- Unit: startup validation of the key (E9, E9b).
- Manual: start with and without the key (E9), `enabled: false` (E5), a live search returning Google hits.

## Verification results (2026-11-16)

| Ref | Check | Result |
|-----|-------|--------|
| E1 | live search | Google hits first, then Open Library |
| E2–E4 | unit (`GoogleBooksClientTest`, fake request factory) | `503,503,200` → 3 calls; `429` → 1 call, guard closed, no further request; quota-`403` same; location-`403` → failure. Not reproducible live |
| E5, E9b | `BOOKTRACKER_GOOGLE_BOOKS_ENABLED=false` | sources `search [open_library]`, Google not called |
| E6 | metrics after scans and adds | no `google_books` ISBN or record call |
| E7 | add Google hit `9783150154786` | 201, row `open_library` / `OL32243904M`; Google showed 120 pages, the CC0 record has none (accepted effect of D1) |
| E7b | add Google hit `9783644107816` | 404, no row |
| E7c, E7e | live search | every result with an id, no repeated ISBN |
| E7d | dev DB | 0 rows with `source = google_books` |
| E8–E8e | unit (`SearchCatalogUseCaseTest`, `CompositeBookMetadataSourceTest`) + live with Google unreachable | Open Library hits still returned; query nobody knows → 503, detail names no provider |
| E9 | start with empty key | refused, message names `BOOKTRACKER_GOOGLE_BOOKS_API_KEY` and `enabled=false` |
| — | key location | loaded from git-ignored `bootstrap/config/application.yaml` (decided with the user 2026-11-16 instead of `backend/.env` + Gradle code) |

## Code review of `4e93225` (2026-11-16)

All 7 findings checked against the code; all valid, all fixed on `feature/google_books` (not committed).

| # | Finding | Verdict | Fix |
|---|---------|---------|-----|
| 1 | Every provider skipped for quota → `[]` instead of 503 | valid; a rule question → D11 | composite throws when nobody answered cleanly |
| 2 | `DailyQuotaGuard.trip` could shorten a daily pause to 60 s | valid (concurrent requests) | `AtomicReference`, never moves the reopening earlier |
| 3 | `InterruptedException` in the backoff → 500, interrupt lost | valid; real once 9.9 cancels parallel calls | restore the interrupt, throw `GoogleBooksException` (degrades) |
| 4 | Google retries delay Open Library | valid trade-off → D12 | 2 attempts |
| 5 | `storeByIsbn` duplicated `ResolveIsbnUseCase` | valid; the copies already differed | `ResolveIsbnUseCase.catalogBook(isbn)`, used by add; one resolution for scan, search and add |
| 6 | Unknown ids in `order` ignored silently | valid | WARN at startup; not a failure, since a disabled provider may stay listed |
| 7 | `ProviderProperties.enabled` never read | valid, minor; "its default can disagree" is inaccurate (missing = off in both) | kept, documented as read by `@ConditionalOnProperty` |

| 8 | (follow-up) An interrupt during Google's retry pause made Open Library fail too: its HTTP client throws "Request was interrupted" (`ResourceAccessException`), which its circuit breaker records against a healthy source | valid, minor (only on interrupts) — checked in `JdkClientHttpRequest` and Resilience4j's `AtomicRateLimiter` | the composite asks no further provider once the thread is interrupted; those count as unable to answer, so hits so far are returned, else 503 |

Verified: `./gradlew build` green, 203 unit tests (12 new). The cases are not reproducible live (spent quota, interrupt, concurrent trips); unit tests cover them.

