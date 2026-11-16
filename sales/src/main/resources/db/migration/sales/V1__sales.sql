-- Book Store schema: sales module, version 1.
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
