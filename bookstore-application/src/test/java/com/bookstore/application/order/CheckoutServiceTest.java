package com.bookstore.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.application.Shop;
import com.bookstore.application.cart.CartChangedException;
import com.bookstore.application.cart.CartRef;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.money.Money;
import com.bookstore.domain.cart.CartAdjustment;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.event.OrderPlaced;
import com.bookstore.domain.event.OrderStatusChanged;
import com.bookstore.domain.order.Order;
import com.bookstore.domain.order.OrderStatus;
import com.bookstore.domain.order.PaymentMethod;

import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    private final Shop shop = new Shop();
    private final Book dune = shop.book("Dune", "100.00", 10);
    private final Book madonna = shop.book("Kürk Mantolu Madonna", "45.50", 3);
    private final long customerId = shop.customer("ayse@example.com");
    private final CartRef cart = CartRef.customer(customerId);

    @Test
    void cardPaymentPlacesAPaidOrderAndEmptiesTheCart() {
        shop.cart.add(cart, dune.id(), 2);
        shop.cart.add(cart, madonna.id(), 1);

        OrderDetails order = place(PaymentMethod.CREDIT_CARD);

        assertThat(order.orderNumber()).isEqualTo("BS-2026-000001");
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.total()).isEqualTo(Money.of("295.40"));
        assertThat(order.shippingAddress()).isEqualTo(Shop.address());
        assertThat(shop.payments.charged()).containsExactly(Money.of("295.40"));
        assertThat(shop.cart.view(cart).isEmpty()).isTrue();
        assertThat(shop.books.findById(dune.id()).orElseThrow().stock().quantity()).isEqualTo(8);
        assertThat(shop.orders.count()).isEqualTo(1);
    }

    @Test
    void customerIsNotifiedOnlyAfterTheOrderIsStored() {
        shop.cart.add(cart, dune.id(), 1);

        place(PaymentMethod.CREDIT_CARD);

        assertThat(shop.notifier.events()).hasSize(2);
        assertThat(shop.notifier.events().get(0)).isInstanceOf(OrderPlaced.class);
        assertThat(shop.notifier.events().get(1)).isInstanceOfSatisfying(OrderStatusChanged.class,
                e -> assertThat(e.to()).isEqualTo(OrderStatus.PAID));
    }

    @Test
    void bankTransferWaitsForPayment() {
        shop.cart.add(cart, dune.id(), 1);

        OrderDetails order = place(PaymentMethod.BANK_TRANSFER);

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.nextStatuses()).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.CANCELLED);
        assertThat(shop.payments.charged()).isEmpty();
        assertThat(shop.notifier.events()).singleElement().isInstanceOf(OrderPlaced.class);
    }

    @Test
    void declinedCardPlacesNoOrderAndKeepsTheCart() {
        shop.cart.add(cart, dune.id(), 1);
        shop.payments.declineNext("Insufficient funds");

        assertThatThrownBy(() -> place(PaymentMethod.CREDIT_CARD))
                .isInstanceOfSatisfying(PaymentDeclinedException.class,
                        e -> assertThat(e.reason()).isEqualTo("Insufficient funds"));

        assertThat(shop.orders.count()).isZero();
        assertThat(shop.cart.itemCount(cart)).isEqualTo(1);
        assertThat(shop.notifier.events()).isEmpty();
    }

    @Test
    void priceChangeStopsCheckoutUntilTheCustomerHasSeenIt() {
        shop.cart.add(cart, dune.id(), 1);
        dune.changePrice(Money.of("120.00"));

        assertThatThrownBy(() -> place(PaymentMethod.CREDIT_CARD))
                .isInstanceOfSatisfying(CartChangedException.class, e -> assertThat(e.adjustments())
                        .containsExactly(new CartAdjustment.PriceChanged(dune.id(), "Dune", Money.of("100.00"),
                                Money.of("120.00"))));
        assertThat(shop.orders.count()).isZero();

        OrderDetails order = place(PaymentMethod.CREDIT_CARD);
        assertThat(order.subtotal()).isEqualTo(Money.of("120.00"));
    }

    @Test
    void bookSoldOutSinceItWasAddedStopsCheckout() {
        shop.cart.add(cart, madonna.id(), 3);
        madonna.changeStock(0);

        assertThatThrownBy(() -> place(PaymentMethod.CREDIT_CARD)).isInstanceOf(CartChangedException.class);
        assertThat(shop.cart.view(cart).isEmpty()).isTrue();
    }

    @Test
    void emptyCartCannotBeOrdered() {
        assertThatThrownBy(() -> place(PaymentMethod.CREDIT_CARD))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(Order.EMPTY_CART));
    }

    @Test
    void addressMustBelongToTheCustomer() {
        long other = shop.customer("mehmet@example.com");
        shop.cart.add(cart, dune.id(), 1);

        assertThatThrownBy(() -> shop.checkout.placeOrder(
                new PlaceOrderCommand(customerId, shop.defaultAddressId(other), PaymentMethod.CREDIT_CARD)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderNumbersAreSequential() {
        shop.cart.add(cart, dune.id(), 1);
        String first = place(PaymentMethod.CREDIT_CARD).orderNumber();
        shop.cart.add(cart, dune.id(), 1);
        String second = place(PaymentMethod.CREDIT_CARD).orderNumber();

        assertThat(first).isEqualTo("BS-2026-000001");
        assertThat(second).isEqualTo("BS-2026-000002");
    }

    @Test
    void laterPriceChangesDoNotAlterThePlacedOrder() {
        shop.cart.add(cart, dune.id(), 1);
        String number = place(PaymentMethod.CREDIT_CARD).orderNumber();

        dune.changePrice(Money.of("500.00"));

        assertThat(shop.myOrders.myOrder(customerId, number).lines().getFirst().unitPrice())
                .isEqualTo(Money.of("100.00"));
    }

    private OrderDetails place(PaymentMethod method) {
        return shop.checkout.placeOrder(new PlaceOrderCommand(customerId, shop.defaultAddressId(customerId), method));
    }
}
