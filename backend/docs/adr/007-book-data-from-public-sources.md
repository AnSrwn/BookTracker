# ADR-007: Book Data from Public Sources, Stored in the Catalog

Details of the current design: [book-data.md](../book-data.md).

- **Context:** Users add books by scanning an ISBN or by searching title/author. A full catalog in our own database from the start was explicitly not wanted: it means a bulk import pipeline, tens of GB of storage and a monthly refresh, all before the first user exists. So the data has to come from public sources at request time. Two constraints make that harder than it sounds:
  - The free tiers are rate-limited **far** below what a pass-through proxy needs. Open Library allows 3 req/s per IP for identified callers, we hold ourselves to 1 req/s, and a backend is one IP for its whole user base.
  - The sources differ in what they are licensed for. Open Library and DNB title data are CC0. The Google APIs Terms of Service forbid building databases or other permanent copies of API content (§5(e)). So Google data is shown at request time and never stored, not in the catalog and not in a private book.
- **Decision:** Three sources behind one port `BookMetadataSource` in `core`, implemented in the module `:adapter:bookdata`. **Google Books finds the book. DNB and Open Library describe the one we keep.** Google's strength is recall and ranking, not field accuracy, and it is the source we may not store. Every CC0 record resolved by ISBN or added by a user is stored in the `book` catalog (`owner_id IS NULL`, [ADR-006](006-catalog-and-private-books.md)). The catalog accumulates exactly the books users look up, and most lookups never leave the backend. A `review_state` column is filtered on from day one, while still defaulting to `APPROVED`.
- **Decision, part two: the provider set is configuration, not structure.** The set will change: DNB may go, a publisher API such as Penguin Random House may arrive.
  - Each provider carries a `SourceDescriptor` (stable lowercase `id`, capabilities, scope, and whether its licence permits storing records publicly). `CompositeBookMetadataSource` stays generic and skips providers that cannot serve a query.
  - Precedence and enablement live in `application.yaml` as **first non-null wins over a configured order**, keyed by language where it matters.
  - `source` in the database is an **open vocabulary** (`TEXT`, no value `CHECK`), because the data outlives the providers.
  - The licence rule is an **allowlist** (`owner_id IS NOT NULL OR source IN (…)`), so an unreviewed provider is search-only by default.
  - In Kotlin, `SourceId` (provider) and `SourceRecordId` (its own key) are value classes, not an enum and not bare `String`s.

**Options for the provider set**

| | Hard-coded merge (`if` per source) | Config-driven order + descriptors ✅ | Plugin SPI / dynamic loading |
|---|---|---|---|
| Add a provider | edit the composite | new client + one config block | implement an interface, register |
| Reorder | code change, release | config change, restart | config change |
| Disable a misbehaving provider in prod | release | `enabled: false` | `enabled: false` |
| Composite knows each source's quirks | yes, and that code rots | no | no |
| Complexity carried | lowest | one properties class | an extension framework nobody needs at 3–5 providers |

**Options for where the data lives**

| | A: Pass-through proxy | B: App calls the APIs itself | C: Proxy + self-growing catalog ✅ | D: Bulk import of the OL dumps |
|---|---|---|---|---|
| Rows in the DB at launch | none | none | none, grows per lookup | millions, before the first user |
| Survives the free rate limits | ❌ 1 req/s shared by every user | partly: per-IP spreads, per-key does not | ✅ the catalog absorbs the repeat traffic | ✅ no external calls at all |
| API keys | backend | **in the app binary, unrotatable** | backend | – |
| Normalisation & merge | backend | per platform, implemented twice | backend | in the import job |
| Search latency | 1 × external | 1 × external | <20 ms on a hit, else 1 × external | <50 ms |
| Who sees the user's search terms | our server | the user's device → Google | our server | nobody |
| Operational cost | none | none | none | pipeline, ~30–60 GB, monthly refresh |

**Sources**

| Source | Role | Licence | Free-tier limit | Intended use |
|---|---|---|---|---|
| Google Books | recall and ranking for free-text search **only**. Its `isbn:` operator is deliberately unused | restrictive, **never** persisted, not even in private books | 1,000 queries/day per key, `429` when spent | own terms, branding and attribution apply |
| DNB SRU | canonical fields for German imprints | **CC0** | free, no registration, no published limit, self-capped at 1 req/s | **no restriction, incl. commercial**: SRU for lookups, OAI-PMH for bulk |
| Open Library | fields and covers outside the German market | **CC0** | 3 req/s with a contact `User-Agent`, **self-capped at 1 req/s** | **mission-aligned, human-facing lookup only**, not a bulk or high-traffic commercial backend |

- **Consequences:**
  - ➕ No catalog to build, host or refresh up front. It fills with the books our users actually own.
  - ➕ Only data we may store is persisted, enforced rather than remembered. A Google hit is added by its ISBN, which resolves to a CC0 record. The use case checks the `SourceDescriptor`, and the allowlist `CHECK` backstops it.
  - ➕ One port, so `core` and the app see one normalised shape instead of three foreign schemas. Adding or dropping a source is an adapter change with no API or app release.
  - ➕ API keys exist only on the server, and the sources see the server's IP. The user's search terms never go to Google from their device.
  - ➕ The scan flow, the MVP's most important path, uses no Google budget, because Google does no ISBN lookup. The 1,000/day limit only constrains free-text search.
  - ➕ Adding a provider is a client, a mapper and a config block. Removing one is `enabled: false` plus deleting its package, with **no data migration**.
  - ➖ A catalog miss costs one external round trip (~200–500 ms). Acceptable only because search is explicit. Search-as-you-type would force option D.
  - ➖ Three response formats to map, one of them MARC21-XML. The field-level merge order is a judgement call and will need tuning.
  - ➖ Correctness and availability depend on third parties. A bad upstream record pollutes the catalog for everyone until a review workflow exists.
  - ➖ Google's daily budget cannot be throttled, so it needs its own degrade path instead of a shared rate limiter.
  - ➖ The generic composite costs a descriptor per provider and a properties class. It stops the fan-out from collecting per-source special cases.
  - ➖ Three things stay manual: one mapper per provider, a licence read per provider, and the judgement of which source is best per field.
  - ➖ Merging relevance-ranked results degrades as the set grows: fine at 2–4 sources, a ranking problem of its own beyond that.
  - ⚠ **Open Library's live API is licensed by intent, not just by rate.** Their policy limits it to mission-aligned, human-facing lookup and excludes a bulk or high-traffic commercial backend. This design fits while BookTracker is private or small and every call answers a human's search. It stops fitting once the backend is commercial or high-traffic, and no rate limiter fixes that.
  - ↪ **So option D is conditional, not optional.** Going professional means moving Open Library off the live API: to the monthly CC0 dumps or to terms agreed at `openlibrary@archive.org`. Google Books and DNB can stay request-time. Option D seeds the same table through the same port, so the move is an import job plus a disabled client, with no API, schema or app change. Budget it as a release, not an afternoon.
  - ↪ If search quality or independence ever outweighs storage, option D can seed the same table for that reason too.
  - ↪ A publisher API (e.g. Penguin Random House) fits as a *scoped* provider: high precedence for its own imprints, nothing elsewhere, and almost certainly search-only under the allowlist.
  - ↪ Caching is additive: a `CachingBookMetadataSource` decorator is one class and one `@Bean`, deferred until the metrics ask for it. Storing into the catalog is **not** deferred, because it is the data model, not a cache: rows without a stable `(source, source_id)` identity cannot be deduped or attributed later.
  - ⚠ **`SourceId` must stay a value class and never become an enum.** An enum would close the vocabulary: a new provider would need a code change, and a retired one could not keep its rows. `SourceId.of` validates the format, not membership.
  - ⚠ Self-limiting breaks with more than one instance: two backends each capped at 1 req/s send 2 req/s and get blocked. Scaling out needs shared state (Redis-backed buckets, e.g. Bucket4j), the same decision as a shared cache.
