package com.bookstore.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.application.admin.OrderAdminService;
import com.bookstore.application.cart.CartRef;
import com.bookstore.application.cart.CartService;
import com.bookstore.application.order.CheckoutService;
import com.bookstore.application.order.OrderDetails;
import com.bookstore.application.order.PaymentDeclinedException;
import com.bookstore.application.order.PlaceOrderCommand;
import com.bookstore.application.port.Notifier;
import com.bookstore.application.port.PaymentGateway;
import com.bookstore.common.error.ConflictException;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.event.DomainEvent;
import com.bookstore.domain.order.OrderNumber;
import com.bookstore.domain.order.OrderStatus;
import com.bookstore.domain.order.PaymentMethod;
import com.bookstore.domain.order.ShippingPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * The application's checkout running on the real JDBC repositories and transactions. These are the
 * guarantees the in-memory fakes of the application module cannot show: a failure rolls back every
 * change, and two customers racing for the last copy cannot both get it.
 */
class CheckoutWithDatabaseTest {

    private final Db db = new Db();
    private final List<DomainEvent> notified = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean declineCards = new AtomicBoolean();

    private final Notifier notifier = notified::add;
    private final PaymentGateway payments = (number, amount) -> declineCards.get()
            ? new PaymentGateway.PaymentResult.Declined("Card declined")
            : new PaymentGateway.PaymentResult.Approved("TX-" + number.value());

    private final CartService carts = new CartService(db.books, db.carts, ShippingPolicy.standard(), db.transactions);
    private final CheckoutService checkout = new CheckoutService(db.customers, db.carts, db.books, db.orders,
            db.orderNumbers, payments, ShippingPolicy.standard(), db.transactions, notifier, Db.CLOCK);
    private final OrderAdminService admin = new OrderAdminService(db.orders, db.books, db.transactions, notifier,
            Db.CLOCK);

    private final Book dune = db.book("Dune", "100.00", 5);
    private final Customer ayse = db.customer("ayse@example.com");

    @Test
    void successfulCheckoutStoresEverythingTogether() {
        carts.add(CartRef.customer(ayse.id()), dune.id(), 2);

        OrderDetails order = place(ayse);

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(dune)).isEqualTo(3);
        assertThat(db.orders.findByNumber(new OrderNumber(order.orderNumber())))
                .hasValueSatisfying(o -> assertThat(o.totalQuantity()).isEqualTo(2));
        assertThat(db.carts.findByCustomerId(ayse.id()).orElseThrow().isEmpty()).isTrue();
        assertThat(notified).hasSize(2);
    }

    @Test
    void declinedPaymentRollsBackTheStockTheOrderAndTheOrderNumber() {
        carts.add(CartRef.customer(ayse.id()), dune.id(), 2);
        declineCards.set(true);

        assertThatThrownBy(() -> place(ayse)).isInstanceOf(PaymentDeclinedException.class);

        assertThat(stockOf(dune)).as("stock taken by Order.place was rolled back").isEqualTo(5);
        assertThat(db.jdbc.queryForLong("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(db.carts.findByCustomerId(ayse.id()).orElseThrow().totalQuantity()).isEqualTo(2);
        assertThat(notified).isEmpty();

        declineCards.set(false);
        assertThat(place(ayse).orderNumber()).as("the rolled-back number is handed out again")
                .isEqualTo("BS-2026-000001");
    }

    @Test
    void cancellingPutsTheStockBackInTheDatabase() {
        carts.add(CartRef.customer(ayse.id()), dune.id(), 4);
        String number = place(ayse).orderNumber();

        admin.changeStatus(number, OrderStatus.CANCELLED);

        assertThat(stockOf(dune)).isEqualTo(5);
        assertThat(admin.order(number).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void twoCustomersRacingForTheLastCopyCannotBothGetIt() throws Exception {
        Book lastCopy = db.book("Last copy", "50.00", 1);
        Customer mehmet = db.customer("mehmet@example.com");
        carts.add(CartRef.customer(ayse.id()), lastCopy.id(), 1);
        carts.add(CartRef.customer(mehmet.id()), lastCopy.id(), 1);

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> attempts = List.of(ayse, mehmet).stream()
                .<Callable<String>>map(customer -> () -> {
                    start.await();
                    try {
                        return place(customer).orderNumber();
                    } catch (ConflictException e) {
                        return "refused: " + e.code();
                    }
                })
                .toList();

        List<String> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<String>> futures = attempts.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<String> future : futures) {
                outcomes.add(future.get());
            }
        }

        assertThat(outcomes).filteredOn(o -> o.startsWith("BS-")).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.startsWith("refused: ")).hasSize(1);
        assertThat(stockOf(lastCopy)).isZero();
        assertThat(db.jdbc.queryForLong("SELECT COUNT(*) FROM orders")).isEqualTo(1);
    }

    private OrderDetails place(Customer customer) {
        long addressId = customer.defaultAddress().orElseThrow().id();
        return checkout.placeOrder(new PlaceOrderCommand(customer.id(), addressId, PaymentMethod.CREDIT_CARD));
    }

    private int stockOf(Book book) {
        return db.books.findById(book.id()).orElseThrow().stock().quantity();
    }
}
