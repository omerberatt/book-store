-- Book Store schema, version 1.
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

-- ---------------------------------------------------------------- customers

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    version       INT          NOT NULL DEFAULT 0,
    email         VARCHAR(254) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at DATETIME(6)  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (CASE role WHEN 'CUSTOMER' THEN 1 WHEN 'ADMIN' THEN 1 ELSE 0 END = 1)
);

CREATE TABLE addresses (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    version      INT          NOT NULL DEFAULT 0,
    user_id      BIGINT       NOT NULL,
    sort_order   INT          NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    label        VARCHAR(50)  NOT NULL,
    recipient    VARCHAR(200) NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    address_line VARCHAR(300) NOT NULL,
    district     VARCHAR(100) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    postal_code  CHAR(5)      NOT NULL,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_addresses_user ON addresses (user_id);

-- ---------------------------------------------------------------- carts

CREATE TABLE carts (
    user_id BIGINT PRIMARY KEY,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    user_id          BIGINT         NOT NULL,
    book_id          BIGINT         NOT NULL,
    sort_order       INT            NOT NULL,
    quantity         INT            NOT NULL,
    price_when_added DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (user_id, book_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (user_id) REFERENCES carts (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 10)
);

-- ---------------------------------------------------------------- orders

CREATE TABLE orders (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    version           INT            NOT NULL DEFAULT 0,
    order_number      CHAR(14)       NOT NULL,
    user_id           BIGINT         NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    payment_method    VARCHAR(20)    NOT NULL,
    shipping_cost     DECIMAL(12, 2) NOT NULL,
    placed_at         DATETIME(6)    NOT NULL,
    status_changed_at DATETIME(6)    NOT NULL,
    -- The delivery address as it was when the order was placed.
    ship_recipient    VARCHAR(200)   NOT NULL,
    ship_phone        VARCHAR(20)    NOT NULL,
    ship_line         VARCHAR(300)   NOT NULL,
    ship_district     VARCHAR(100)   NOT NULL,
    ship_city         VARCHAR(100)   NOT NULL,
    ship_postal_code  CHAR(5)        NOT NULL,
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_orders_status CHECK (CASE status WHEN 'CREATED' THEN 1 WHEN 'PAID' THEN 1 WHEN 'SHIPPED' THEN 1
        WHEN 'DELIVERED' THEN 1 WHEN 'CANCELLED' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_orders_shipping CHECK (shipping_cost >= 0)
);

CREATE INDEX idx_orders_user ON orders (user_id, placed_at);
CREATE INDEX idx_orders_status ON orders (status, placed_at);

CREATE TABLE order_lines (
    order_id   BIGINT         NOT NULL,
    line_no    INT            NOT NULL,
    book_id    BIGINT         NOT NULL,
    -- Title and price are copied when the order is placed.
    book_title VARCHAR(300)   NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity   INT            NOT NULL,
    PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_lines_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT ck_order_lines_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_lines_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_lines_book ON order_lines (book_id);

-- One counter per year for order numbers such as BS-2026-000042.
CREATE TABLE order_number_sequences (
    order_year  INT    PRIMARY KEY,
    last_number BIGINT NOT NULL
);

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
