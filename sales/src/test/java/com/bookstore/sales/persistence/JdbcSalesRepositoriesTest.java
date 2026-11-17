package com.bookstore.sales.persistence;

import static com.bookstore.sales.persistence.SalesDb.snapshotOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.customer.domain.Customer;
import com.bookstore.sales.domain.Cart;
import com.bookstore.sales.domain.CartItem;
import com.bookstore.sales.domain.Order;
import com.bookstore.sales.domain.OrderLine;
import com.bookstore.sales.domain.OrderNumber;
import com.bookstore.sales.domain.OrderStatus;
import com.bookstore.sales.domain.PaymentMethod;
import com.bookstore.sales.domain.ProductSnapshot;
import com.bookstore.sales.domain.ShippingPolicy;
import com.bookstore.shared.jdbc.DataAccessException;
import com.bookstore.shared.jdbc.JdbcExecutor;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.testing.TestAddresses;
import com.bookstore.shared.kernel.testing.TestClock;

import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JdbcSalesRepositoriesTest {

    private final SalesDb db = new SalesDb();

    @Test
    void referenceTablesListEveryStatusAndPaymentMethodOfTheDomain() {
        assertThat(db.jdbc.query("SELECT code FROM order_statuses", row -> row.getString("code")))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(OrderStatus.values()).map(Enum::name).toList());
        assertThat(db.jdbc.query("SELECT code FROM payment_methods", row -> row.getString("code")))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(PaymentMethod.values()).map(Enum::name).toList());
    }

    @Nested
    class Carts {

        private final Customer customer = db.customers.customer("ayse@example.com");
        private final ProductSnapshot dune = snapshotOf(db.catalog.book("Dune", "100", 10));
        private final ProductSnapshot madonna = snapshotOf(db.catalog.book("Kürk Mantolu Madonna", "45.50", 3));

        @Test
        void customerWithoutACartHasNone() {
            assertThat(db.carts.findByCustomerId(customer.id())).isEmpty();
        }

        @Test
        void linesKeepTheirOrderQuantityAndPrice() {
            Cart cart = Cart.forCustomer(customer.id());
            cart.add(madonna, 2);
            cart.add(dune, 1);
            db.carts.save(cart);

            Cart loaded = db.carts.findByCustomerId(customer.id()).orElseThrow();

            assertThat(loaded.items()).containsExactly(new CartItem(madonna.bookId(), 2, Money.of("45.50")),
                    new CartItem(dune.bookId(), 1, Money.of("100.00")));
        }

        @Test
        void savingReplacesAllLines() {
            Cart cart = Cart.forCustomer(customer.id());
            cart.add(dune, 1);
            db.carts.save(cart);
            cart.clear();
            db.carts.save(cart);

            assertThat(db.carts.findByCustomerId(customer.id())).hasValueSatisfying(c -> assertThat(c.isEmpty()).isTrue());
        }

        @Test
        void guestCartsAreNotStored() {
            assertThatThrownBy(() -> db.carts.save(Cart.guest())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void quantityIsCappedAtTenByTheDatabaseToo() {
            db.jdbc.update("INSERT INTO carts (user_id) VALUES (?)", customer.id());

            assertThatThrownBy(() -> db.jdbc.update("INSERT INTO cart_items (user_id, book_id, sort_order, quantity, "
                    + "price_when_added) VALUES (?, ?, 0, 11, 100)", customer.id(), dune.bookId()))
                    .isInstanceOfSatisfying(DataAccessException.class,
                            e -> assertThat(e.isConstraintViolation()).isTrue());
        }
    }

    @Nested
    class Orders {

        private final Customer ayse = db.customers.customer("ayse@example.com");
        private final Customer mehmet = db.customers.customer("mehmet@example.com");
        private final ProductSnapshot dune = snapshotOf(db.catalog.book("Dune", "100", 10));
        private final ProductSnapshot madonna = snapshotOf(db.catalog.book("Kürk Mantolu Madonna", "45.50", 3));

        @Test
        void orderWithLinesAndAddressSurvivesTheRoundTrip() {
            Order placed = place(ayse, TestClock.CLOCK, Map.of(dune, 2, madonna, 1));

            Order loaded = db.orders.findByNumber(placed.number()).orElseThrow();

            assertThat(loaded.id()).isEqualTo(placed.id());
            assertThat(loaded.customerId()).isEqualTo(ayse.id());
            assertThat(loaded.lines()).containsExactlyInAnyOrderElementsOf(placed.lines());
            assertThat(loaded.lines()).extracting(OrderLine::bookTitle).containsExactlyElementsOf(
                    placed.lines().stream().map(OrderLine::bookTitle).toList());
            assertThat(loaded.shippingAddress()).isEqualTo(TestAddresses.home());
            assertThat(loaded.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(loaded.total()).isEqualTo(placed.total());
            assertThat(loaded.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(loaded.placedAt()).isEqualTo(TestClock.NOW);
        }

        @Test
        void statusChangesAreStored() {
            Order order = place(ayse, TestClock.CLOCK, Map.of(dune, 1));
            Clock later = Clock.offset(TestClock.CLOCK, Duration.ofDays(1));
            order.markPaid(later);
            db.orders.save(order);

            Order loaded = db.orders.findById(order.id()).orElseThrow();
            assertThat(loaded.status()).isEqualTo(OrderStatus.PAID);
            assertThat(loaded.statusChangedAt()).isEqualTo(later.instant());
            assertThat(loaded.version()).isEqualTo(1);
        }

        @Test
        void twoAdministratorsCannotBothChangeTheSameOrder() {
            Order order = place(ayse, TestClock.CLOCK, Map.of(dune, 1));
            Order first = db.orders.findById(order.id()).orElseThrow();
            Order second = db.orders.findById(order.id()).orElseThrow();

            first.markPaid(TestClock.CLOCK);
            db.orders.save(first);
            second.cancel(TestClock.CLOCK);

            assertThatThrownBy(() -> db.orders.save(second))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.code()).isEqualTo(JdbcExecutor.CONCURRENT_MODIFICATION));
        }

        @Test
        void listsAreNewestFirstAndFiltered() {
            Order old = place(ayse, TestClock.CLOCK, Map.of(dune, 1));
            Order recent = place(ayse, Clock.offset(TestClock.CLOCK, Duration.ofHours(2)), Map.of(dune, 1));
            Order other = place(mehmet, Clock.offset(TestClock.CLOCK, Duration.ofHours(1)), Map.of(madonna, 1));
            other.markPaid(TestClock.CLOCK);
            db.orders.save(other);

            assertThat(db.orders.findByCustomerId(ayse.id(), PageRequest.first()).content())
                    .extracting(Order::number).containsExactly(recent.number(), old.number());
            assertThat(db.orders.findAll(PageRequest.first()).content())
                    .extracting(Order::number).containsExactly(recent.number(), other.number(), old.number());
            assertThat(db.orders.findByStatus(OrderStatus.PAID, PageRequest.first()).content())
                    .extracting(Order::number).containsExactly(other.number());
            assertThat(db.orders.findByStatus(OrderStatus.DELIVERED, PageRequest.first()).isEmpty()).isTrue();
        }

        @Test
        void knowsWhoReceivedWhichBook() {
            Order order = place(ayse, TestClock.CLOCK, Map.of(dune, 1));
            assertThat(db.orders.hasDeliveredBook(ayse.id(), dune.bookId())).isFalse();

            order.markPaid(TestClock.CLOCK);
            order.ship(TestClock.CLOCK);
            order.deliver(TestClock.CLOCK);
            db.orders.save(order);

            assertThat(db.orders.hasDeliveredBook(ayse.id(), dune.bookId())).isTrue();
            assertThat(db.orders.hasDeliveredBook(ayse.id(), madonna.bookId())).isFalse();
            assertThat(db.orders.hasDeliveredBook(mehmet.id(), dune.bookId())).isFalse();
        }

        private Order place(Customer customer, Clock clock, Map<ProductSnapshot, Integer> lines) {
            Cart cart = Cart.forCustomer(customer.id());
            lines.forEach(cart::add);
            Map<BookId, ProductSnapshot> products = new LinkedHashMap<>();
            lines.keySet().forEach(product -> products.put(product.bookId(), product));
            Order order = Order.place(db.orderNumbers.next(Year.of(2026)), customer.id(), cart, products,
                    TestAddresses.home(), PaymentMethod.BANK_TRANSFER, ShippingPolicy.standard(), clock);
            return db.orders.save(order);
        }
    }

    @Nested
    class OrderNumbers {

        @Test
        void countPerYear() {
            assertThat(db.orderNumbers.next(Year.of(2026)).value()).isEqualTo("BS-2026-000001");
            assertThat(db.orderNumbers.next(Year.of(2026)).value()).isEqualTo("BS-2026-000002");
            assertThat(db.orderNumbers.next(Year.of(2027)).value()).isEqualTo("BS-2027-000001");
        }

        @Test
        void neverHandOutTheSameNumberTwiceUnderConcurrency() throws Exception {
            Set<OrderNumber> numbers = ConcurrentHashMap.newKeySet();
            try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    tasks.add(pool.submit(() -> numbers.add(db.orderNumbers.next(Year.of(2026)))));
                }
                for (Future<?> task : tasks) {
                    task.get();
                }
            }

            assertThat(numbers).hasSize(200);
            assertThat(numbers).extracting(OrderNumber::sequence).contains(1L, 200L);
        }
    }
}
