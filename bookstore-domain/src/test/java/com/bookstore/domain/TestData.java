package com.bookstore.domain;

import com.bookstore.common.money.Money;
import com.bookstore.domain.catalog.Author;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.Category;
import com.bookstore.domain.catalog.Isbn;
import com.bookstore.domain.catalog.Stock;
import com.bookstore.domain.customer.Address;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Ready-made, already "stored" domain objects for tests.
 */
public final class TestData {

    /** A clock stopped at a fixed moment, so tests can assert exact times. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

    /** The moment {@link #CLOCK} is stopped at. */
    public static final Instant NOW = CLOCK.instant();

    /** A customer id used as the owner of carts and orders. */
    public static final long CUSTOMER_ID = 7;

    private TestData() {
    }

    /** @return a stored root category "Literature" (id 1) with a stored child "Novel" (id 2) */
    public static Category novels() {
        Category literature = Category.restoreRoot(1, 0, "Literature");
        return Category.restoreChild(2, 0, "Novel", literature);
    }

    /** @return a stored author */
    public static Author author(long id, String name) {
        return Author.restore(id, 0, name);
    }

    /**
     * Returns a valid ISBN-13 built from a running number.
     *
     * @param n any number below one billion
     * @return a valid ISBN
     */
    public static Isbn isbn(long n) {
        String body = "978" + String.format("%09d", n);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return new Isbn(body + (10 - sum % 10) % 10);
    }

    /** @return a stored, active book in {@link #novels()} */
    public static Book book(long id, String title, String price, int stock) {
        return bookBuilder(id, title, price, stock).build();
    }

    /** @return a builder for a stored, active book, for tests that need to change more attributes */
    public static Book.Builder bookBuilder(long id, String title, String price, int stock) {
        return Book.builder()
                .persisted(id, 0)
                .isbn(isbn(id))
                .title(title)
                .author(author(100 + id, "Author " + id))
                .category(novels())
                .price(Money.of(price))
                .stock(Stock.of(stock));
    }

    /** @return a valid delivery address */
    public static Address address() {
        return new Address("Ayşe Yılmaz", "+90 532 000 00 00", "Bağdat Cad. No: 1 D: 2", "Kadıköy", "İstanbul",
                "34710");
    }
}
