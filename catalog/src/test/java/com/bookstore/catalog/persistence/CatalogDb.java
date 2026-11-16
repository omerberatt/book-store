package com.bookstore.catalog.persistence;

import com.bookstore.catalog.domain.Author;
import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.domain.Category;
import com.bookstore.catalog.domain.Isbn;
import com.bookstore.catalog.domain.Stock;
import com.bookstore.shared.jdbc.JdbcExecutor;
import com.bookstore.shared.jdbc.JdbcTransactionRunner;
import com.bookstore.shared.jdbc.TestDatabase;
import com.bookstore.shared.kernel.money.Money;

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/**
 * A fresh H2 database with the catalog module's tables and repositories, plus helpers that store
 * typical catalog objects. Other modules' persistence tests use it to create the books their rows
 * refer to.
 */
public final class CatalogDb {

    public final DataSource dataSource;
    public final JdbcTransactionRunner transactions;
    public final JdbcExecutor jdbc;
    public final JdbcCategoryRepository categories;
    public final JdbcAuthorRepository authors;
    public final JdbcBookRepository books;

    private final AtomicInteger counter = new AtomicInteger();

    /** Creates a database with only the catalog module's tables. */
    public CatalogDb() {
        this(TestDatabase.withModules("catalog"));
    }

    /**
     * Uses a database created by another module's test, which must include the catalog tables.
     *
     * @param database the database
     */
    public CatalogDb(TestDatabase database) {
        dataSource = database.dataSource();
        transactions = database.transactions();
        jdbc = database.jdbc();
        categories = new JdbcCategoryRepository(jdbc);
        authors = new JdbcAuthorRepository(jdbc);
        books = new JdbcBookRepository(jdbc, categories);
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
