-- Nullable on purpose: NULL marks a public catalog entry, a value makes the book
-- private to that user. See ADR-006.
ALTER TABLE book
    ADD COLUMN owner_id UUID;

CREATE INDEX idx_book_owner_id ON book (owner_id);
