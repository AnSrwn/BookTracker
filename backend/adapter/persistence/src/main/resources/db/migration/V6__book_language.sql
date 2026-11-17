-- The edition's language as a BCP 47 primary language subtag ('de', 'en'; three letters only where
-- no two-letter code exists), normalised by Language in core (9.9 Q7, Q9). NULL where unknown,
-- and for every row written before 9.9: no backfill.
ALTER TABLE book
    ADD COLUMN language TEXT CHECK (language ~ '^[a-z]{2,3}$');
