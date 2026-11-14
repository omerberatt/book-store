package com.bookstore.domain.order;

import static com.bookstore.domain.TestData.CLOCK;
import static com.bookstore.domain.TestData.CUSTOMER_ID;
import static com.bookstore.domain.TestData.NOW;
import static com.bookstore.domain.TestData.address;
import static com.bookstore.domain.TestData.book;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.common.error.AccessDeniedException;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.money.Money;
import com.bookstore.domain.cart.Cart;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.InsufficientStockException;
import com.bookstore.domain.event.DomainEvent;
import com.bookstore.domain.event.OrderPlaced;
import com.bookstore.domain.event.OrderStatusChanged;

import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OrderTest {

    private static final OrderNumber NUMBER = OrderNumber.of(Year.of(2026), 42);

    private final Book dune = book(1, "Dune", "100.00", 10);
    private final Book madonna = book(2, "Kürk Mantolu Madonna", "45.50", 3);
    private final Map<Long, Book> books = Map.of(1L, dune, 2L, madonna);
    private final Cart cart = Cart.forCustomer(CUSTOMER_ID);

    @Test
    void placingCopiesTheCartAndWorksOutTheTotals() {
        cart.add(dune, 2);
        cart.add(madonna, 1);

        Order order = place();

        assertThat(order.number()).isEqualTo(NUMBER);
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.placedAt()).isEqualTo(NOW);
        assertThat(order.lines()).containsExactly(
                new OrderLine(1, "Dune", Money.of("100.00"), 2),
                new OrderLine(2, "Kürk Mantolu Madonna", Money.of("45.50"), 1));
        assertThat(order.subtotal()).isEqualTo(Money.of("245.50"));
        assertThat(order.shippingCost()).isEqualTo(Money.of("49.90"));
        assertThat(order.total()).isEqualTo(Money.of("295.40"));
        assertThat(order.totalQuantity()).isEqualTo(3);
        assertThat(order.contains(2)).isTrue();
        assertThat(order.isOwnedBy(CUSTOMER_ID)).isTrue();
    }

    @Test
    void placingTakesTheCopiesOutOfStock() {
        cart.add(dune, 2);
        cart.add(madonna, 3);

        place();

        assertThat(dune.stock().quantity()).isEqualTo(8);
        assertThat(madonna.stock().quantity()).isZero();
    }

    @Test
    void shippingIsFreeFromTheThreshold() {
        cart.add(dune, 5);

        Order order = place();

        assertThat(order.subtotal()).isEqualTo(Money.of("500.00"));
        assertThat(order.shippingCost()).isEqualTo(Money.ZERO);
    }

    @Test
    void placingRaisesOrderPlacedOnce() {
        cart.add(dune, 1);
        Order order = place();

        assertThat(order.pullEvents()).containsExactly(
                new OrderPlaced(NUMBER, CUSTOMER_ID, Money.of("149.90"), NOW));
        assertThat(order.pullEvents()).isEmpty();
    }

    @Test
    void laterPriceChangesDoNotAffectThePlacedOrder() {
        cart.add(dune, 1);
        Order order = place();

        dune.changePrice(Money.of("999.00"));
        dune.changeDetails("Dune (New Edition)", null, null, dune.language(), 0, null);

        assertThat(order.lines().getFirst().unitPrice()).isEqualTo(Money.of("100.00"));
        assertThat(order.lines().getFirst().bookTitle()).isEqualTo("Dune");
    }

    @Test
    void whenOneLineIsShortNoStockIsTaken() {
        cart.add(dune, 2);
        cart.add(madonna, 3);
        madonna.changeStock(1);

        assertThatThrownBy(this::place).isInstanceOf(InsufficientStockException.class);
        assertThat(dune.stock().quantity()).isEqualTo(10);
        assertThat(madonna.stock().quantity()).isEqualTo(1);
    }

    @Test
    void cannotPlaceAnEmptyCart() {
        assertThatThrownBy(this::place)
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(Order.EMPTY_CART));
    }

    @Test
    void cannotPlaceSomeoneElsesOrAGuestCart() {
        Cart other = Cart.forCustomer(99);
        other.add(dune, 1);
        Cart guest = Cart.guest();
        guest.add(dune, 1);

        assertThatThrownBy(() -> place(other)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> place(guest)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cannotOrderABookThatIsNoLongerSold() {
        cart.add(dune, 1);
        dune.deactivate();

        assertThatThrownBy(this::place).isInstanceOf(ConflictException.class).hasMessageContaining("no longer sold");
    }

    @Test
    void followsTheHappyPathAndRaisesAnEventForEveryChange() {
        cart.add(dune, 1);
        Order order = place();
        order.pullEvents();
        Clock later = Clock.offset(CLOCK, Duration.ofHours(1));

        order.markPaid(later);
        order.ship(later);
        order.deliver(later);

        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.statusChangedAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(order.pullEvents())
                .extracting(OrderTest::target)
                .containsExactly(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    }

    @Test
    void rejectsSkippingSteps() {
        cart.add(dune, 1);
        Order order = place();

        assertThatThrownBy(() -> order.ship(CLOCK))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(OrderStatus.ILLEGAL_TRANSITION));
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void cancellingPutsTheCopiesBackIntoStock() {
        cart.add(dune, 4);
        Order order = place();
        order.markPaid(CLOCK);

        order.cancel(books, CLOCK);

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(dune.stock().quantity()).isEqualTo(10);
    }

    @Test
    void shippedOrderCannotBeCancelledAndStockStaysTheSame() {
        cart.add(dune, 4);
        Order order = place();
        order.markPaid(CLOCK);
        order.ship(CLOCK);

        assertThatThrownBy(() -> order.cancel(books, CLOCK)).isInstanceOf(ConflictException.class);
        assertThat(dune.stock().quantity()).isEqualTo(6);
    }

    @Test
    void restoreRecomputesTheSubtotal() {
        Order order = Order.restore(5, 2, NUMBER, CUSTOMER_ID,
                java.util.List.of(new OrderLine(1, "Dune", Money.of("100"), 3)), address(),
                PaymentMethod.BANK_TRANSFER, Money.ZERO, OrderStatus.SHIPPED, NOW, NOW);

        assertThat(order.id()).isEqualTo(5);
        assertThat(order.version()).isEqualTo(2);
        assertThat(order.total()).isEqualTo(Money.of("300.00"));
        assertThat(order.pullEvents()).isEmpty();
    }

    private Order place() {
        return place(cart);
    }

    private Order place(Cart someCart) {
        return Order.place(NUMBER, CUSTOMER_ID, someCart, books, address(), PaymentMethod.CREDIT_CARD,
                ShippingPolicy.standard(), CLOCK);
    }

    private static OrderStatus target(DomainEvent event) {
        return switch (event) {
            case OrderStatusChanged changed -> changed.to();
            case OrderPlaced placed -> throw new AssertionError("unexpected " + placed);
        };
    }
}
