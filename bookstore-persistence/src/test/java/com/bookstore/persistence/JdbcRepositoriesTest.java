package com.bookstore.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.common.error.ConflictException;
import com.bookstore.common.money.Money;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.cart.Cart;
import com.bookstore.domain.cart.CartItem;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.customer.Address;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.customer.Email;
import com.bookstore.domain.customer.PersonName;
import com.bookstore.domain.customer.Role;
import com.bookstore.domain.customer.SavedAddress;
import com.bookstore.domain.order.Order;
import com.bookstore.domain.order.OrderLine;
import com.bookstore.domain.order.OrderNumber;
import com.bookstore.domain.order.OrderStatus;
import com.bookstore.domain.order.PaymentMethod;
import com.bookstore.domain.order.ShippingPolicy;
import com.bookstore.domain.review.Rating;
import com.bookstore.domain.review.Review;
import com.bookstore.persistence.jdbc.JdbcExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JdbcRepositoriesTest {

    private final Db db = new Db();

    @Nested
    class Customers {

        @Test
        void customerAndAddressesSurviveTheRoundTrip() {
            Customer saved = db.customer("Ayse@Example.com");

            Customer loaded = db.customers.findByEmail(Email.of("ayse@example.com")).orElseThrow();

            assertThat(loaded.id()).isEqualTo(saved.id());
            assertThat(loaded.name()).isEqualTo(new PersonName("Ayşe", "Yılmaz"));
            assertThat(loaded.passwordHash()).isEqualTo("{hash}pw");
            assertThat(loaded.role()).isEqualTo(Role.CUSTOMER);
            assertThat(loaded.isActive()).isTrue();
            assertThat(loaded.registeredAt()).isEqualTo(Db.CLOCK.instant());
            assertThat(loaded.addresses()).singleElement().satisfies(a -> {
                assertThat(a.label()).isEqualTo("Ev");
                assertThat(a.address()).isEqualTo(Db.address());
            });
            assertThat(loaded.defaultAddress()).isPresent();
        }

        @Test
        void addressBookChangesAreStored() {
            Customer customer = db.customer("ayse@example.com");
            long home = customer.addresses().getFirst().id();
            Address work = new Address("Ayşe Yılmaz", "+90 212 000 00 00", "Büyükdere Cad. 100", "Şişli", "İstanbul",
                    "34394");

            customer.addAddress("İş", work, true);
            db.customers.save(customer);
            Customer reloaded = db.customers.findById(customer.id()).orElseThrow();
            assertThat(reloaded.addresses()).extracting(SavedAddress::label).containsExactly("Ev", "İş");
            assertThat(reloaded.defaultAddress()).map(SavedAddress::label).contains("İş");

            reloaded.removeAddress(reloaded.defaultAddress().orElseThrow().id());
            reloaded.changeAddress(home, "Yazlık", Db.address());
            db.customers.save(reloaded);
            Customer last = db.customers.findById(customer.id()).orElseThrow();
            assertThat(last.addresses()).extracting(SavedAddress::label).containsExactly("Yazlık");
            assertThat(last.defaultAddress()).map(SavedAddress::id).contains(home);
        }

        @Test
        void emailIsUnique() {
            db.customer("ayse@example.com");
            Customer twin = Customer.register(Email.of("AYSE@example.com"), new PersonName("A", "B"), "h", Db.CLOCK);

            assertThatThrownBy(() -> db.customers.save(twin))
                    .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("email.taken"));
            assertThat(db.customers.existsByEmail(Email.of("ayse@example.com"))).isTrue();
        }

        @Test
        void profileChangesAreStoredWithANewVersion() {
            Customer customer = db.customer("ayse@example.com");
            customer.rename(new PersonName("Ayşe Nur", "Kaya"));
            customer.changePasswordHash("{hash}new");
            customer.deactivate();
            db.customers.save(customer);

            Customer loaded = db.customers.findById(customer.id()).orElseThrow();
            assertThat(loaded.name().fullName()).isEqualTo("Ayşe Nur Kaya");
            assertThat(loaded.passwordHash()).isEqualTo("{hash}new");
            assertThat(loaded.isActive()).isFalse();
            assertThat(loaded.version()).isEqualTo(1);
        }

        @Test
        void listsAndLooksUpSeveralAtOnce() {
            Customer a = db.customer("a@example.com");
            Customer b = db.customer("b@example.com");

            Page<Customer> page = db.customers.findAll(PageRequest.first());

            assertThat(page.content()).extracting(c -> c.email().value()).containsExactly("b@example.com", "a@example.com");
            assertThat(db.customers.findAllById(List.of(a.id(), b.id(), 999L))).hasSize(2);
        }
    }

    @Nested
    class Carts {

        private final Customer customer = db.customer("ayse@example.com");
        private final Book dune = db.book("Dune", "100", 10);
        private final Book madonna = db.book("Kürk Mantolu Madonna", "45.50", 3);

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

            assertThat(loaded.items()).containsExactly(new CartItem(madonna.id(), 2, Money.of("45.50")),
                    new CartItem(dune.id(), 1, Money.of("100.00")));
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
    }

    @Nested
    class Orders {

        private final Customer ayse = db.customer("ayse@example.com");
        private final Customer mehmet = db.customer("mehmet@example.com");
        private final Book dune = db.book("Dune", "100", 10);
        private final Book madonna = db.book("Kürk Mantolu Madonna", "45.50", 3);

        @Test
        void orderWithLinesAndAddressSurvivesTheRoundTrip() {
            Order placed = place(ayse, Db.CLOCK, Map.of(dune, 2, madonna, 1));

            Order loaded = db.orders.findByNumber(placed.number()).orElseThrow();

            assertThat(loaded.id()).isEqualTo(placed.id());
            assertThat(loaded.customerId()).isEqualTo(ayse.id());
            assertThat(loaded.lines()).containsExactlyInAnyOrderElementsOf(placed.lines());
            assertThat(loaded.lines()).extracting(OrderLine::bookTitle).containsExactlyElementsOf(
                    placed.lines().stream().map(OrderLine::bookTitle).toList());
            assertThat(loaded.shippingAddress()).isEqualTo(Db.address());
            assertThat(loaded.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
            assertThat(loaded.total()).isEqualTo(placed.total());
            assertThat(loaded.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(loaded.placedAt()).isEqualTo(Db.CLOCK.instant());
        }

        @Test
        void statusChangesAreStored() {
            Order order = place(ayse, Db.CLOCK, Map.of(dune, 1));
            Clock later = Clock.offset(Db.CLOCK, Duration.ofDays(1));
            order.markPaid(later);
            db.orders.save(order);

            Order loaded = db.orders.findById(order.id()).orElseThrow();
            assertThat(loaded.status()).isEqualTo(OrderStatus.PAID);
            assertThat(loaded.statusChangedAt()).isEqualTo(later.instant());
            assertThat(loaded.version()).isEqualTo(1);
        }

        @Test
        void twoAdministratorsCannotBothChangeTheSameOrder() {
            Order order = place(ayse, Db.CLOCK, Map.of(dune, 1));
            Order first = db.orders.findById(order.id()).orElseThrow();
            Order second = db.orders.findById(order.id()).orElseThrow();

            first.markPaid(Db.CLOCK);
            db.orders.save(first);
            second.cancel(Map.of(dune.id(), dune), Db.CLOCK);

            assertThatThrownBy(() -> db.orders.save(second))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.code()).isEqualTo(JdbcExecutor.CONCURRENT_MODIFICATION));
        }

        @Test
        void listsAreNewestFirstAndFiltered() {
            Order old = place(ayse, Db.CLOCK, Map.of(dune, 1));
            Order recent = place(ayse, Clock.offset(Db.CLOCK, Duration.ofHours(2)), Map.of(dune, 1));
            Order other = place(mehmet, Clock.offset(Db.CLOCK, Duration.ofHours(1)), Map.of(madonna, 1));
            other.markPaid(Db.CLOCK);
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
            Order order = place(ayse, Db.CLOCK, Map.of(dune, 1));
            assertThat(db.orders.hasDeliveredBook(ayse.id(), dune.id())).isFalse();

            order.markPaid(Db.CLOCK);
            order.ship(Db.CLOCK);
            order.deliver(Db.CLOCK);
            db.orders.save(order);

            assertThat(db.orders.hasDeliveredBook(ayse.id(), dune.id())).isTrue();
            assertThat(db.orders.hasDeliveredBook(ayse.id(), madonna.id())).isFalse();
            assertThat(db.orders.hasDeliveredBook(mehmet.id(), dune.id())).isFalse();
        }

        private Order place(Customer customer, Clock clock, Map<Book, Integer> lines) {
            Cart cart = Cart.forCustomer(customer.id());
            lines.forEach(cart::add);
            Map<Long, Book> books = new java.util.HashMap<>();
            lines.keySet().forEach(book -> books.put(book.id(), book));
            Order order = Order.place(db.orderNumbers.next(Year.of(2026)), customer.id(), cart, books, Db.address(),
                    PaymentMethod.BANK_TRANSFER, ShippingPolicy.standard(), clock);
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

    @Nested
    class Reviews {

        private final Customer ayse = db.customer("ayse@example.com");
        private final Customer mehmet = db.customer("mehmet@example.com");
        private final Book dune = db.book("Dune", "100", 10);

        @Test
        void reviewSurvivesTheRoundTripAndEdits() {
            Review review = db.reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(4), "Güzel", Db.CLOCK));

            Review loaded = db.reviews.findByBookIdAndCustomerId(dune.id(), ayse.id()).orElseThrow();
            assertThat(loaded.id()).isEqualTo(review.id());
            assertThat(loaded.rating()).isEqualTo(Rating.of(4));
            assertThat(loaded.comment()).isEqualTo("Güzel");
            assertThat(loaded.createdAt()).isEqualTo(Db.CLOCK.instant());

            loaded.edit(Rating.of(5), null, Clock.offset(Db.CLOCK, Duration.ofDays(1)));
            db.reviews.save(loaded);
            Review edited = db.reviews.findById(review.id()).orElseThrow();
            assertThat(edited.rating()).isEqualTo(Rating.of(5));
            assertThat(edited.comment()).isEmpty();
            assertThat(edited.updatedAt()).isAfter(edited.createdAt());
        }

        @Test
        void oneReviewPerCustomerAndBook() {
            db.reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(4), null, Db.CLOCK));

            assertThatThrownBy(() -> db.reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(1), null, Db.CLOCK)))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.code()).isEqualTo("review.duplicate"));
        }

        @Test
        void listsRatingsAndDeletes() {
            Review first = db.reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(5), null, Db.CLOCK));
            db.reviews.save(Review.write(dune.id(), mehmet.id(), Rating.of(2), null,
                    Clock.offset(Db.CLOCK, Duration.ofMinutes(1))));

            assertThat(db.reviews.findByBookId(dune.id(), PageRequest.first()).content())
                    .extracting(Review::customerId).containsExactly(mehmet.id(), ayse.id());
            assertThat(db.reviews.findRatingsByBookId(dune.id())).containsExactlyInAnyOrder(Rating.of(5), Rating.of(2));
            assertThat(db.reviews.findAll(PageRequest.first()).totalElements()).isEqualTo(2);

            db.reviews.delete(first);
            assertThat(db.reviews.findRatingsByBookId(dune.id())).containsExactly(Rating.of(2));
        }
    }
}
