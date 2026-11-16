package com.bookstore.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.catalog.domain.Book;
import com.bookstore.shared.jdbc.DataAccessException;

import org.junit.jupiter.api.Test;

/**
 * The catalog tables' own constraints, checked with raw SQL that bypasses the domain. They are the
 * last line of defence if a bug ever lets an invalid value through.
 */
class CatalogSchemaConstraintsTest {

    private final CatalogDb db = new CatalogDb();
    private final Book book = db.book("Dune", "100", 5);

    @Test
    void stockCannotGoNegative() {
        assertViolation("UPDATE books SET stock = -1 WHERE id = ?", book.id());
    }

    @Test
    void priceMustBePositive() {
        assertViolation("UPDATE books SET price = 0 WHERE id = ?", book.id());
    }

    @Test
    void ratingTotalsMustBePossible() {
        assertViolation("UPDATE books SET rating_count = 1, rating_sum = 6 WHERE id = ?", book.id());
    }

    @Test
    void foreignKeysPreventOrphans() {
        assertViolation("INSERT INTO book_authors (book_id, author_id, sort_order) VALUES (?, 999, 0)", book.id());
    }

    private void assertViolation(String sql, Object... params) {
        assertThatThrownBy(() -> db.jdbc.update(sql, params))
                .isInstanceOfSatisfying(DataAccessException.class,
                        e -> assertThat(e.isConstraintViolation()).as(e.getMessage()).isTrue());
    }
}
