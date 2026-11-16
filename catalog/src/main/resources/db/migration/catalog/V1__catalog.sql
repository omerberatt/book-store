-- Book Store schema: catalog module, version 1.
--
-- Each business module owns its tables and migrations (ADR-0002, ADR-0018); it refers to no other module's tables.
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

-- ---------------------------------------------------------------- catalog

CREATE TABLE categories (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    version   INT          NOT NULL DEFAULT 0,
    parent_id BIGINT       NULL,
    name      VARCHAR(100) NOT NULL,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    CONSTRAINT uq_categories_parent_name UNIQUE (parent_id, name)
);

CREATE TABLE authors (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    version  INT          NOT NULL DEFAULT 0,
    name     VARCHAR(200) NOT NULL,
    -- Lower-cased name without accents or Turkish letters, for searching (see TextNormalizer).
    name_key VARCHAR(200) NOT NULL
);

CREATE INDEX idx_authors_name_key ON authors (name_key);

CREATE TABLE books (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    version          INT            NOT NULL DEFAULT 0,
    isbn             CHAR(13)       NOT NULL,
    title            VARCHAR(300)   NOT NULL,
    -- Lower-cased title without accents or Turkish letters, for searching and sorting.
    title_key        VARCHAR(300)   NOT NULL,
    category_id      BIGINT         NOT NULL,
    publisher        VARCHAR(200)   NOT NULL DEFAULT '',
    publication_year INT            NULL,
    language         CHAR(2)        NOT NULL,
    page_count       INT            NOT NULL DEFAULT 0,
    description      VARCHAR(5000)  NOT NULL DEFAULT '',
    price            DECIMAL(12, 2) NOT NULL,
    stock            INT            NOT NULL DEFAULT 0,
    cover_image      VARCHAR(255)   NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    rating_count     INT            NOT NULL DEFAULT 0,
    rating_sum       INT            NOT NULL DEFAULT 0,
    CONSTRAINT uq_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_books_price CHECK (price > 0),
    CONSTRAINT ck_books_stock CHECK (stock >= 0),
    CONSTRAINT ck_books_ratings CHECK (rating_count >= 0 AND rating_sum >= rating_count AND rating_sum <= rating_count * 5)
);

CREATE INDEX idx_books_title_key ON books (title_key);
CREATE INDEX idx_books_category ON books (category_id);

CREATE TABLE book_authors (
    book_id    BIGINT NOT NULL,
    author_id  BIGINT NOT NULL,
    -- Order in which the authors are listed on the book.
    sort_order INT    NOT NULL,
    PRIMARY KEY (book_id, author_id),
    CONSTRAINT fk_book_authors_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_authors_author FOREIGN KEY (author_id) REFERENCES authors (id)
);

CREATE INDEX idx_book_authors_author ON book_authors (author_id);
