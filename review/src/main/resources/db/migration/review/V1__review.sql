-- Book Store schema: review module, version 1.
--
-- Each business module owns its tables and migrations (ADR-0002, ADR-0018); its foreign keys refer to catalog (books) and customer (users), which are migrated first.
--
-- Written in SQL that runs on both MySQL 8.4 and H2 in MySQL mode:
--   * no ENGINE / CHARSET clauses (MySQL 8 defaults to InnoDB and utf8mb4),
--   * no ON UPDATE CURRENT_TIMESTAMP (times are set by the application),
--   * no reserved words as column names (for example `year`, `value`, `comment`),
--   * no IN (...) lists in CHECK constraints: H2 2.4 evaluates those with the session that created
--     the table, which fails once the migration connection is closed. H2 also rewrites chains of
--     `x = 'A' OR x = 'B'` into the same IN form, so allowed values are listed in a CASE instead.
-- Times are stored as DATETIME(6) in UTC.
-- Tables that are updated concurrently carry a `version` column for optimistic locking.

-- ---------------------------------------------------------------- reviews

CREATE TABLE reviews (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    version      INT           NOT NULL DEFAULT 0,
    book_id      BIGINT        NOT NULL,
    user_id      BIGINT        NOT NULL,
    rating       INT           NOT NULL,
    comment_text VARCHAR(2000) NOT NULL DEFAULT '',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    CONSTRAINT uq_reviews_user_book UNIQUE (user_id, book_id),
    CONSTRAINT fk_reviews_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_book ON reviews (book_id, created_at);
