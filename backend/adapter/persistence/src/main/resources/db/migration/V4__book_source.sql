-- Catalog provenance. `source` is an open vocabulary (no value CHECK): the data outlives
-- the providers. Existing rows become 'manual'. See architecture.md, Database & Migrations.
ALTER TABLE book
    ADD COLUMN source            TEXT        NOT NULL DEFAULT 'manual',
    ADD COLUMN source_id         TEXT,
    ADD COLUMN source_fetched_at TIMESTAMPTZ,
    -- Our own workflow, so a closed vocabulary is fine here. Every catalog read filters on it.
    ADD COLUMN review_state      TEXT        NOT NULL DEFAULT 'APPROVED'
        CHECK (review_state IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- Open Library work key where known (ADR-008). Nothing reads it yet.
    ADD COLUMN work_key          TEXT;

-- Only licensed sources may enter the public catalog. An allowlist, so it fails closed:
-- a new provider is search-only until this constraint is deliberately extended.
ALTER TABLE book
    ADD CONSTRAINT book_public_source_allowlist
        CHECK (owner_id IS NOT NULL OR source IN ('manual', 'dnb', 'open_library'));

-- ISBN alone cannot dedupe the catalog: some records carry none.
CREATE UNIQUE INDEX ux_book_catalog_source ON book (source, source_id) WHERE owner_id IS NULL;

-- The same ISBN only once: per catalog, and per owner (ADR-006).
CREATE UNIQUE INDEX ux_book_catalog_isbn ON book (isbn) WHERE owner_id IS NULL AND isbn IS NOT NULL;
CREATE UNIQUE INDEX ux_book_owner_isbn ON book (owner_id, isbn) WHERE owner_id IS NOT NULL AND isbn IS NOT NULL;

-- Authors in credit order, owned by the book. `source` is the issuer of the author key,
-- not book.source: a merged record mixes sources.
CREATE TABLE book_author
(
    book_id          UUID    NOT NULL REFERENCES book (id) ON DELETE CASCADE,
    position         INTEGER NOT NULL,
    name             TEXT    NOT NULL CHECK (name <> ''),
    source           TEXT,
    source_author_id TEXT,
    PRIMARY KEY (book_id, position),
    CHECK ((source IS NULL) = (source_author_id IS NULL))
);

INSERT INTO book_author (book_id, position, name)
SELECT id, 0, author
FROM book;

ALTER TABLE book
    DROP COLUMN author;
