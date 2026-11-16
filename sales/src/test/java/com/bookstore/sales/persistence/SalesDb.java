package com.bookstore.sales.persistence;

import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.persistence.CatalogDb;
import com.bookstore.customer.persistence.CustomerDb;
import com.bookstore.sales.domain.ProductSnapshot;
import com.bookstore.shared.jdbc.JdbcExecutor;
import com.bookstore.shared.jdbc.TestDatabase;

/**
 * A test database with the catalog, customer and sales tables, and the sales repositories on it.
 * Books and users that sales rows refer to are created through the other modules' fixtures.
 */
public final class SalesDb {

    public final TestDatabase database = TestDatabase.withModules("catalog", "customer", "sales");
    public final JdbcExecutor jdbc = database.jdbc();
    public final CatalogDb catalog = new CatalogDb(database);
    public final CustomerDb customers = new CustomerDb(database);
    public final JdbcCartRepository carts = new JdbcCartRepository(jdbc);
    public final JdbcOrderRepository orders = new JdbcOrderRepository(jdbc);
    public final JdbcOrderNumberSequence orderNumbers = new JdbcOrderNumberSequence(jdbc, database.transactions());

    /** Returns the sales module's view of a stored book. */
    public static ProductSnapshot snapshotOf(Book book) {
        return new ProductSnapshot(book.id(), book.title(), book.authorNames(), book.price(), book.stock().quantity(),
                book.isActive());
    }
}
