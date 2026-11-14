package com.bookstore.persistence;

import com.bookstore.common.money.Money;
import com.bookstore.domain.catalog.Author;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.Category;
import com.bookstore.domain.catalog.Isbn;
import com.bookstore.domain.catalog.Stock;
import com.bookstore.domain.customer.Address;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.customer.Email;
import com.bookstore.domain.customer.PersonName;
import com.bookstore.persistence.cart.JdbcCartRepository;
import com.bookstore.persistence.catalog.JdbcAuthorRepository;
import com.bookstore.persistence.catalog.JdbcBookRepository;
import com.bookstore.persistence.catalog.JdbcCategoryRepository;
import com.bookstore.persistence.customer.JdbcCustomerRepository;
import com.bookstore.persistence.jdbc.JdbcExecutor;
import com.bookstore.persistence.jdbc.JdbcTransactionRunner;
import com.bookstore.persistence.order.JdbcOrderNumberSequence;
import com.bookstore.persistence.order.JdbcOrderRepository;
import com.bookstore.persistence.review.JdbcReviewRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

/**
 * A fresh, migrated in-memory H2 database in MySQL mode with every repository wired to it. Each test
 * creates its own, so tests never see each other's data.
 */
public final class Db {

    /** A clock stopped at a fixed moment. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

    public final DataSource dataSource;
    public final JdbcTransactionRunner transactions;
    public final JdbcExecutor jdbc;

    public final JdbcCategoryRepository categories;
    public final JdbcAuthorRepository authors;
    public final JdbcBookRepository books;
    public final JdbcCustomerRepository customers;
    public final JdbcCartRepository carts;
    public final JdbcOrderRepository orders;
    public final JdbcOrderNumberSequence orderNumbers;
    public final JdbcReviewRepository reviews;

    private final AtomicInteger counter = new AtomicInteger();

    public Db() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        h2.setUser("sa");
        dataSource = h2;
        DatabaseMigrator.migrate(dataSource);

        transactions = new JdbcTransactionRunner(dataSource);
        jdbc = new JdbcExecutor(transactions);
        categories = new JdbcCategoryRepository(jdbc);
        authors = new JdbcAuthorRepository(jdbc);
        books = new JdbcBookRepository(jdbc, categories);
        customers = new JdbcCustomerRepository(jdbc);
        carts = new JdbcCartRepository(jdbc);
        orders = new JdbcOrderRepository(jdbc);
        orderNumbers = new JdbcOrderNumberSequence(jdbc, transactions);
        reviews = new JdbcReviewRepository(jdbc);
    }

    /** Stores a root category. */
    public Category rootCategory(String name) {
        return categories.save(Category.root(name));
    }

    /** Stores an author. */
    public Author author(String name) {
        return authors.save(Author.create(name));
    }

    /** Returns a builder for a book with a fresh ISBN in a new category by a new author. */
    public Book.Builder bookBuilder(String title, String price, int stock) {
        return Book.builder()
                .isbn(nextIsbn())
                .title(title)
                .author(author("Author of " + title))
                .category(rootCategory("Category " + counter.incrementAndGet()))
                .price(Money.of(price))
                .stock(Stock.of(stock));
    }

    /** Stores a book with a fresh ISBN in a new category by a new author. */
    public Book book(String title, String price, int stock) {
        return books.save(bookBuilder(title, price, stock).build());
    }

    /** Stores a customer with one default address. */
    public Customer customer(String email) {
        Customer customer = Customer.register(Email.of(email), new PersonName("Ayşe", "Yılmaz"), "{hash}pw", CLOCK);
        customer.addAddress("Ev", address(), true);
        return customers.save(customer);
    }

    /** A valid address. */
    public static Address address() {
        return new Address("Ayşe Yılmaz", "+90 532 000 00 00", "Bağdat Cad. No: 1", "Kadıköy", "İstanbul", "34710");
    }

    /** Returns a valid ISBN that has not been used in this database. */
    public Isbn nextIsbn() {
        String body = "978" + String.format("%09d", counter.incrementAndGet());
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return new Isbn(body + (10 - sum % 10) % 10);
    }
}
