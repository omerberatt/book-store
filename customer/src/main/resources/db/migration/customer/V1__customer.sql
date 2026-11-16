-- Book Store schema: customer module, version 1.
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
