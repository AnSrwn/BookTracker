CREATE TABLE book
(
    id               UUID PRIMARY KEY,
    title            TEXT NOT NULL,
    author           TEXT NOT NULL,
    publisher        TEXT,
    isbn             VARCHAR(13),
    publication_year INTEGER,
    page_count       INTEGER CHECK (page_count > 0),
    cover_url        TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);