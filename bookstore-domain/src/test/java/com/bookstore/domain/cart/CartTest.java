package com.bookstore.domain.cart;

import static com.bookstore.domain.TestData.CUSTOMER_ID;
import static com.bookstore.domain.TestData.book;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.ValidationException;
import com.bookstore.common.money.Money;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.InsufficientStockException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CartTest {

    private static final Money DUNE_PRICE = Money.of("100.00");
    private static final Money MADONNA_PRICE = Money.of("45.50");

    private final Book dune = book(1, "Dune", "100.00", 20);
    private final Book madonna = book(2, "Kürk Mantolu Madonna", "45.50", 3);
    private final Cart cart = Cart.forCustomer(CUSTOMER_ID);

    @Test
    void addingTheSameBookAddsUpTheQuantity() {
        cart.add(dune, 2);
        cart.add(dune, 3);

        assertThat(cart.quantityOf(1)).isEqualTo(5);
        assertThat(cart.items()).containsExactly(new CartItem(1, 5, DUNE_PRICE));
    }

    @Test
    void linesKeepTheOrderBooksWereFirstAdded() {
        cart.add(madonna, 1);
        cart.add(dune, 1);
        cart.add(madonna, 1);

        List<Long> order = new ArrayList<>();
        for (CartItem item : cart) {
            order.add(item.bookId());
        }
        assertThat(order).containsExactly(2L, 1L);
        assertThat(cart.totalQuantity()).isEqualTo(3);
    }

    @Test
    void quantityPerLineIsCappedAtTen() {
        cart.add(dune, 10);

        assertThatThrownBy(() -> cart.add(dune, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 10");
        assertThat(cart.quantityOf(1)).isEqualTo(10);
    }

    @Test
    void quantityCannotExceedTheStock() {
        assertThatThrownBy(() -> cart.add(madonna, 4)).isInstanceOf(InsufficientStockException.class);
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void outOfStockOrInactiveBooksCannotBeAdded() {
        Book soldOut = book(3, "Sold out", "10", 0);
        Book inactive = book(4, "Inactive", "10", 5);
        inactive.deactivate();

        assertThatThrownBy(() -> cart.add(soldOut, 1)).isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> cart.add(inactive, 1)).isInstanceOf(ConflictException.class);
    }

    @Test
    void changingTheQuantityToZeroRemovesTheLine() {
        cart.add(dune, 2);
        cart.changeQuantity(dune, 7);
        assertThat(cart.quantityOf(1)).isEqualTo(7);

        cart.changeQuantity(dune, 0);
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.quantityOf(1)).isZero();
    }

    @Test
    void subtotalUsesPricesSuppliedByTheCaller() {
        cart.add(dune, 2);
        cart.add(madonna, 3);
        Map<Long, Money> prices = Map.of(1L, dune.price(), 2L, madonna.price());

        assertThat(cart.subtotal(prices::get)).isEqualTo(Money.of("336.50"));
        assertThat(Cart.guest().subtotal(prices::get)).isEqualTo(Money.ZERO);
    }

    @Test
    void mergingAGuestCartAddsUpAndCapsQuantities() {
        cart.add(dune, 8);
        Cart guest = Cart.guest();
        guest.add(dune, 5);
        guest.add(madonna, 1);

        cart.mergeFrom(guest);

        assertThat(cart.items()).containsExactly(new CartItem(1, 10, DUNE_PRICE), new CartItem(2, 1, MADONNA_PRICE));
        assertThat(guest.items()).hasSize(2);
        assertThat(cart.customerId()).contains(CUSTOMER_ID);
        assertThat(guest.customerId()).isEmpty();
    }

    @Test
    void iteratorDoesNotAllowRemoval() {
        cart.add(dune, 1);
        Iterator<CartItem> iterator = cart.iterator();
        iterator.next();

        assertThatThrownBy(iterator::remove).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(iterator::next).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void guestCartSurvivesJavaSerialization() throws Exception {
        Cart guest = Cart.guest();
        guest.add(dune, 2);
        guest.add(madonna, 1);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(guest);
        }
        Cart copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (Cart) in.readObject();
        }

        assertThat(copy.items()).containsExactly(new CartItem(1, 2, DUNE_PRICE), new CartItem(2, 1, MADONNA_PRICE));
        assertThat(copy.customerId()).isEmpty();
    }

    @Test
    void reconcileRemovesUnsellableBooksLowersQuantitiesAndNotesNewPrices() {
        Book soonInactive = book(3, "Soon inactive", "10", 5);
        Book soonGone = book(4, "Soon gone", "10", 5);
        cart.add(dune, 5);
        cart.add(madonna, 3);
        cart.add(soonInactive, 1);
        cart.add(soonGone, 1);

        madonna.changeStock(1);
        dune.changePrice(Money.of("90.00"));
        soonInactive.deactivate();

        List<CartAdjustment> adjustments = cart.reconcile(Map.of(1L, dune, 2L, madonna, 3L, soonInactive));

        assertThat(adjustments).containsExactly(
                new CartAdjustment.PriceChanged(1, "Dune", DUNE_PRICE, Money.of("90.00")),
                new CartAdjustment.QuantityReduced(2, "Kürk Mantolu Madonna", 3, 1),
                new CartAdjustment.Removed(3, "Soon inactive"),
                new CartAdjustment.Removed(4, "#4"));
        assertThat(cart.items()).containsExactly(
                new CartItem(1, 5, Money.of("90.00")), new CartItem(2, 1, MADONNA_PRICE));
    }

    @Test
    void reconcilingAnUpToDateCartChangesNothing() {
        cart.add(dune, 2);

        assertThat(cart.reconcile(Map.of(1L, dune))).isEmpty();
        assertThat(cart.reconcile(Map.of(1L, dune))).isEmpty();
        assertThat(cart.items()).containsExactly(new CartItem(1, 2, DUNE_PRICE));
    }

    @Test
    void mergeKeepsThePriceTheCustomerSawFirst() {
        cart.add(dune, 1);
        dune.changePrice(Money.of("80.00"));
        Cart guest = Cart.guest();
        guest.add(dune, 1);

        cart.mergeFrom(guest);

        assertThat(cart.items()).containsExactly(new CartItem(1, 2, DUNE_PRICE));
    }

    @Test
    void restoreRebuildsTheLines() {
        Cart restored = Cart.restore(CUSTOMER_ID, List.of(new CartItem(2, 1, MADONNA_PRICE), new CartItem(1, 4, DUNE_PRICE)));

        assertThat(restored.bookIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(restored.items()).first().isEqualTo(new CartItem(2, 1, MADONNA_PRICE));
    }
}
