package com.bookstore.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.customer.Customer;
import com.bookstore.persistence.jdbc.DataAccessException;

import org.junit.jupiter.api.Test;

/**
 * The database's own constraints, checked with raw SQL that bypasses the domain. They are the last
 * line of defence if a bug ever lets an invalid value through.
 */
class SchemaConstraintsTest {

    private final Db db = new Db();
    private final Book book = db.book("Dune", "100", 5);
    private final Customer customer = db.customer("ayse@example.com");

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
    void roleMustBeKnown() {
        assertViolation("UPDATE users SET role = 'HACKER' WHERE id = ?", customer.id());
        db.jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", customer.id());
        assertThat(db.customers.findById(customer.id()).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    void reviewRatingMustBeOneToFive() {
        assertViolation("INSERT INTO reviews (book_id, user_id, rating, comment_text, created_at, updated_at) "
                + "VALUES (?, ?, 6, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", book.id(), customer.id());
    }

    @Test
    void cartQuantityIsCappedAtTen() {
        db.jdbc.update("INSERT INTO carts (user_id) VALUES (?)", customer.id());
        assertViolation("INSERT INTO cart_items (user_id, book_id, sort_order, quantity, price_when_added) "
                + "VALUES (?, ?, 0, 11, 100)", customer.id(), book.id());
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
