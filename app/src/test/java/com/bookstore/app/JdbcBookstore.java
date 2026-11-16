package com.bookstore.app;

import com.bookstore.catalog.application.CatalogService;
import com.bookstore.catalog.application.RatingService;
import com.bookstore.catalog.application.StockService;
import com.bookstore.catalog.persistence.CatalogDb;
import com.bookstore.customer.application.CustomerDirectoryService;
import com.bookstore.customer.persistence.CustomerDb;
import com.bookstore.customer.persistence.JdbcCustomerRepository;
import com.bookstore.review.application.ReviewService;
import com.bookstore.review.persistence.JdbcReviewRepository;
import com.bookstore.sales.application.CartService;
import com.bookstore.sales.application.CheckoutService;
import com.bookstore.sales.application.OrderAdminService;
import com.bookstore.sales.application.PurchaseHistoryService;
import com.bookstore.sales.domain.PaymentGateway;
import com.bookstore.sales.domain.ShippingPolicy;
import com.bookstore.sales.persistence.JdbcCartRepository;
import com.bookstore.sales.persistence.JdbcOrderNumberSequence;
import com.bookstore.sales.persistence.JdbcOrderRepository;
import com.bookstore.shared.jdbc.JdbcTransactionRunner;
import com.bookstore.shared.jdbc.ModuleMigrations;
import com.bookstore.shared.jdbc.TestDatabase;
import com.bookstore.shared.kernel.event.DomainEvent;
import com.bookstore.shared.kernel.event.Notifier;
import com.bookstore.shared.kernel.testing.TestClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All four business modules wired together on one H2 database with their JDBC repositories, the
 * way the composition root will wire them (phase 4.4). Modules reach each other only through their
 * api facades: sales and review get the catalog's {@link StockService} and {@link RatingService},
 * the customer module's {@link CustomerDirectoryService} and the sales module's
 * {@link PurchaseHistoryService}, typed as the interfaces they implement.
 */
final class JdbcBookstore {

    final TestDatabase database = TestDatabase.withModules(ModuleMigrations.ALL.toArray(String[]::new));
    final JdbcTransactionRunner transactions = database.transactions();
    final CatalogDb catalogDb = new CatalogDb(database);
    final CustomerDb customerDb = new CustomerDb(database);

    final List<DomainEvent> notified = Collections.synchronizedList(new ArrayList<>());
    final AtomicBoolean declineCards = new AtomicBoolean();
    private final Notifier notifier = notified::add;
    private final PaymentGateway payments = (number, amount) -> declineCards.get()
            ? new PaymentGateway.PaymentResult.Declined("Card declined")
            : new PaymentGateway.PaymentResult.Approved("TX-" + number.value());

    final CatalogService catalog = new CatalogService(catalogDb.books, catalogDb.categories);
    final StockService stock = new StockService(catalogDb.books, transactions);
    final RatingService ratings = new RatingService(catalogDb.books, transactions);
    final CustomerDirectoryService directory = new CustomerDirectoryService(new JdbcCustomerRepository(database.jdbc()));

    final JdbcOrderRepository orders = new JdbcOrderRepository(database.jdbc());
    final JdbcCartRepository carts = new JdbcCartRepository(database.jdbc());
    final CartService cart = new CartService(stock, carts, ShippingPolicy.standard(), transactions);
    final CheckoutService checkout = new CheckoutService(directory, carts, stock, orders,
            new JdbcOrderNumberSequence(database.jdbc(), transactions), payments, ShippingPolicy.standard(),
            transactions, notifier, TestClock.CLOCK);
    final OrderAdminService orderAdmin = new OrderAdminService(orders, stock, transactions, notifier, TestClock.CLOCK);
    final PurchaseHistoryService purchases = new PurchaseHistoryService(orders);

    final ReviewService reviews = new ReviewService(new JdbcReviewRepository(database.jdbc()), ratings, purchases,
            directory, transactions, TestClock.CLOCK);
}
