# ADR-008: The Edition Is the Unit of the Catalog

- **Context:** A barcode scan yields the ISBN of **one specific printing**. The same work appears under many ISBNs: *Eragon* exists as the cbj paperback `9783570402931` (733 pages), the e-book `9783641023027`, an English Knopf edition, a Georgian translation, and an audiobook. Bibliography distinguishes **work** (what Paolini wrote), **edition** (a specific printing, which carries an ISBN) and **item** (the copy on your shelf). Open Library models the first two (`OL…W` vs `OL…M`). DNB records and Google volumes are editions. A `book` row has to be one of these, and the choice decides what the app can express.
- **Decision:** A row is an **edition**, identified by its ISBN where it has one.
  - The deciding constraint is in the MVP: reading progress is "page X of Y", and **the page count belongs to the edition**. 733 pages for that paperback, no fixed pagination for the e-book, none for the audiobook.
  - Possession is edition-level too, and the scanned barcode *is* the edition, so no resolution step is needed.
  - A **nullable `work_key`** (Open Library's work key where known, `NULL` otherwise) allows grouping editions later without migrating rows. No `work` table and no work-level features in the MVP.
  - ISBN lookup never groups: the user wants the edition they scanned. Free-text search does not group either, for now. The user picks a specific edition there as well, the merge already folds the same edition from several sources into one hit, and a title heuristic groups badly (*Eragon – Das Vermächtnis…* vs. *Eragon 01. Das Vermächtnis…*). Revisit when the app's search screen shows the need.

| | Edition as the row ✅ | Work as the row | Separate `work` + `edition` tables |
|---|---|---|---|
| Page count, reading progress | exact | **impossible**: whose pagination? | exact |
| Barcode scan maps to | the row directly | needs edition → work resolution | the edition row |
| "Have I read this?" across editions | ❌ not without `work_key` | ✅ | ✅ |
| Free-text result noise | several rows per work, needs display grouping | none | none |
| Rows per work | n | 1 | 1 + n |
| Effort now | **none**: this is the current schema | rework, and loses pagination | two tables, two mappers, a join |

- **Consequences:**
  - ➕ Progress and possession are exact. Nothing in the MVP has to be approximated.
  - ➕ The scan flow needs no extra step: the scanned ISBN *is* the row's identity. The ISBN partial indexes and `(source, source_id)` already assume this granularity.
  - ➖ Free-text search returns several editions of one work, which can look like duplicates. If display grouping (normalised title + author) comes, it stays a display heuristic, never a data model.
  - ➖ "Have I read this?" does not carry across editions: read the hardcover, scan the paperback, and the app says no. Irrelevant for the MVP (no statistics, no history), relevant after it.
  - ➖ Non-print editions (audiobooks, online resources) are editions too. They are filtered when mapping: an app about pages read has no use for them.
  - ↪ `work_key` is the migration-free path to grouping: fill it from Open Library where available, leave it `NULL` elsewhere. An option, not a promise: Open Library does not have every edition.
  - ↪ If work-level features arrive, a `work` table can be added with editions pointing at it. Only `work_key` needs backfilling, and nothing that references `book` changes.
  - ⚠ **A title+author fingerprint is not a work key.** It would not join *Eldest* with *Der Auftrag des Ältesten* (the same work in two languages) and would wrongly join unrelated books sharing a title. Such heuristics are for display grouping only, never for identity.
