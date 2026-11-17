package com.bookstore.review.persistence;

import com.bookstore.catalog.persistence.CatalogDb;
import com.bookstore.customer.persistence.CustomerDb;
import com.bookstore.review.domain.ReviewRepository;
import com.bookstore.shared.jdbc.JdbcExecutor;
import com.bookstore.shared.jdbc.TestDatabase;

/**
 * The review repository on a test database (H2 or PostgreSQL) with every module's tables, or in
 * memory. The books and users that reviews refer to are created through the other modules'
 * fixtures. {@link #jdbc} is {@code null} in memory.
 */
public final class ReviewDb {

    public final JdbcExecutor jdbc;
    public final CatalogDb catalog;
    public final CustomerDb customers;
    public final ReviewRepository reviews;

    /** Creates a fresh H2 database. */
    public ReviewDb() {
        this(TestDatabase.withModules("catalog", "customer", "sales", "review"));
    }

    /**
     * Uses a database that has every module's tables.
     *
     * @param database the database
     */
    public ReviewDb(TestDatabase database) {
        this.jdbc = database.jdbc();
        this.catalog = new CatalogDb(database);
        this.customers = new CustomerDb(database);
        this.reviews = new JdbcReviewRepository(jdbc);
    }

    private ReviewDb(CatalogDb catalog, CustomerDb customers) {
        this.jdbc = null;
        this.catalog = catalog;
        this.customers = customers;
        this.reviews = new InMemoryReviewRepository();
    }

    /**
     * Creates the review module's in-memory repository, with in-memory catalog and customers.
     *
     * @return the fixture
     */
    public static ReviewDb inMemory() {
        return new ReviewDb(CatalogDb.inMemory(), CustomerDb.inMemory());
    }
}
