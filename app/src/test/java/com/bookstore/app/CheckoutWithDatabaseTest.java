package com.bookstore.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.catalog.domain.Book;
import com.bookstore.customer.domain.Customer;
import com.bookstore.sales.application.CartRef;
import com.bookstore.sales.application.OrderDetails;
import com.bookstore.sales.application.PaymentDeclinedException;
import com.bookstore.sales.application.PlaceOrderCommand;
import com.bookstore.sales.domain.OrderNumber;
import com.bookstore.sales.domain.OrderStatus;
import com.bookstore.sales.domain.PaymentMethod;
import com.bookstore.shared.kernel.error.ConflictException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/**
 * Checkout across the catalog, customer and sales modules, on the real JDBC repositories and
 * transactions. These are the guarantees the in-memory fakes cannot show: a failure rolls back every
 * change in every module, and two customers racing for the last copy cannot both get it.
 */
class CheckoutWithDatabaseTest {

    private final JdbcBookstore store = new JdbcBookstore();

    private final Book dune = store.catalogDb.book("Dune", "100.00", 5);
    private final Customer ayse = store.customerDb.customer("ayse@example.com");

    @Test
    void successfulCheckoutStoresEverythingTogether() {
        store.cart.add(CartRef.customer(ayse.id()), dune.id(), 2);

        OrderDetails order = place(ayse);

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(dune)).isEqualTo(3);
        assertThat(store.orders.findByNumber(new OrderNumber(order.orderNumber())))
                .hasValueSatisfying(o -> assertThat(o.totalQuantity()).isEqualTo(2));
        assertThat(store.carts.findByCustomerId(ayse.id()).orElseThrow().isEmpty()).isTrue();
        assertThat(store.notified).hasSize(2);
    }

    @Test
    void declinedPaymentRollsBackTheStockTheOrderAndTheOrderNumber() {
        store.cart.add(CartRef.customer(ayse.id()), dune.id(), 2);
        store.declineCards.set(true);

        assertThatThrownBy(() -> place(ayse)).isInstanceOf(PaymentDeclinedException.class);

        assertThat(stockOf(dune)).as("stock taken by Order.place was rolled back").isEqualTo(5);
        assertThat(store.database.jdbc().queryForLong("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(store.carts.findByCustomerId(ayse.id()).orElseThrow().totalQuantity()).isEqualTo(2);
        assertThat(store.notified).isEmpty();

        store.declineCards.set(false);
        assertThat(place(ayse).orderNumber()).as("the rolled-back number is handed out again")
                .isEqualTo("BS-2026-000001");
    }

    @Test
    void cancellingPutsTheStockBackInTheDatabase() {
        store.cart.add(CartRef.customer(ayse.id()), dune.id(), 4);
        String number = place(ayse).orderNumber();

        store.orderAdmin.changeStatus(number, OrderStatus.CANCELLED);

        assertThat(stockOf(dune)).isEqualTo(5);
        assertThat(store.orderAdmin.order(number).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void twoCustomersRacingForTheLastCopyCannotBothGetIt() throws Exception {
        Book lastCopy = store.catalogDb.book("Last copy", "50.00", 1);
        Customer mehmet = store.customerDb.customer("mehmet@example.com");
        store.cart.add(CartRef.customer(ayse.id()), lastCopy.id(), 1);
        store.cart.add(CartRef.customer(mehmet.id()), lastCopy.id(), 1);

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
        assertThat(store.database.jdbc().queryForLong("SELECT COUNT(*) FROM orders")).isEqualTo(1);
    }

    private OrderDetails place(Customer customer) {
        long addressId = customer.defaultAddress().orElseThrow().id().value();
        return store.checkout.placeOrder(new PlaceOrderCommand(customer.id(), addressId, PaymentMethod.CREDIT_CARD));
    }

    private int stockOf(Book book) {
        return store.catalogDb.books.findById(book.id()).orElseThrow().stock().quantity();
    }
}
