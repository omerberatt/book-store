package com.bookstore.app;

import com.bookstore.catalog.api.CatalogFacade;
import com.bookstore.catalog.persistence.CatalogDb;
import com.bookstore.customer.application.CustomerShop;
import com.bookstore.customer.persistence.CustomerDb;
import com.bookstore.review.api.ReviewFacade;
import com.bookstore.sales.api.CartFacade;
import com.bookstore.sales.api.CheckoutFacade;
import com.bookstore.sales.api.OrderAdminFacade;
import com.bookstore.sales.domain.PaymentGateway;
import com.bookstore.sales.persistence.JdbcCartRepository;
import com.bookstore.sales.persistence.JdbcOrderRepository;
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
 * The whole application, wired by {@link BookstoreModule} on one H2 database, for end-to-end
 * tests. Tests drive it through the modules' façades; the catalog and customer fixtures and two
 * repositories are there to set up data and to look at what was stored.
 */
final class JdbcBookstore {

    final TestDatabase database = TestDatabase.withModules(ModuleMigrations.ALL.toArray(String[]::new));
    final CatalogDb catalogDb = new CatalogDb(database);
    final CustomerDb customerDb = new CustomerDb(database);

    final List<DomainEvent> notified = Collections.synchronizedList(new ArrayList<>());
    final AtomicBoolean declineCards = new AtomicBoolean();
    final AtomicBoolean notificationsFail = new AtomicBoolean();
    private final Notifier notifier = event -> {
        if (notificationsFail.get()) {
            throw new IllegalStateException("Mail server unreachable");
        }
        notified.add(event);
    };
    private final PaymentGateway payments = (number, amount) -> declineCards.get()
            ? new PaymentGateway.PaymentResult.Declined("Card declined")
            : new PaymentGateway.PaymentResult.Approved("TX-" + number.value());

    final BookstoreModule module = new BookstoreModule(database.dataSource(),
            new BookstoreModule.Adapters(TestClock.CLOCK, payments, new CustomerShop.PrefixPasswordHasher(), notifier));

    final CatalogFacade catalog = module.catalog();
    final CartFacade cart = module.cart();
    final CheckoutFacade checkout = module.checkout();
    final OrderAdminFacade orderAdmin = module.orderAdmin();
    final ReviewFacade reviews = module.reviews();

    final JdbcOrderRepository orders = new JdbcOrderRepository(database.jdbc());
    final JdbcCartRepository carts = new JdbcCartRepository(database.jdbc());
}
