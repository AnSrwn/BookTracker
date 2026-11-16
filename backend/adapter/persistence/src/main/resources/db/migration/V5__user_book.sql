-- A user's books: links to catalog rows (shared, never copied) and to the user's own private rows.
-- No foreign key on user_id: Keycloak owns identities, same as book.owner_id. See ADR-006.
CREATE TABLE user_book
(
    user_id    UUID        NOT NULL,
    book_id    UUID        NOT NULL REFERENCES book (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, book_id)
);

CREATE INDEX idx_user_book_book_id ON user_book (book_id);

-- Every private book belongs to its owner's books. Catalog rows, including the legacy rows
-- V3 turned into catalog entries, are linked to nobody.
INSERT INTO user_book (user_id, book_id, created_at)
SELECT owner_id, id, created_at
FROM book
WHERE owner_id IS NOT NULL;

-- Private books are independent entities: the same ISBN may exist several times per owner.
-- The catalog stays unique per ISBN (ux_book_catalog_isbn).
DROP INDEX ux_book_owner_isbn;
