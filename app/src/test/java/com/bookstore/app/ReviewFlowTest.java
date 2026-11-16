package com.bookstore.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.catalog.domain.Book;
import com.bookstore.customer.domain.Customer;
import com.bookstore.review.application.ReviewCommand;
import com.bookstore.review.domain.Rating;
import com.bookstore.sales.application.CartRef;
import com.bookstore.sales.application.PlaceOrderCommand;
import com.bookstore.sales.domain.OrderStatus;
import com.bookstore.sales.domain.PaymentMethod;
import com.bookstore.shared.kernel.error.AccessDeniedException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * A review touches three other modules: sales says whether the customer received the book, the
 * catalog keeps the rating totals and the customer module gives the author's public name. This
 * test runs that path on the real database.
 */
class ReviewFlowTest {

    private final JdbcBookstore store = new JdbcBookstore();
    private final Book dune = store.catalogDb.book("Dune", "100.00", 5);
    private final Customer ayse = store.customerDb.customer("ayse@example.com");

    @Test
    void aDeliveredBookCanBeReviewedAndTheCatalogShowsTheRating() {
        assertThatThrownBy(() -> review(5)).isInstanceOf(AccessDeniedException.class);

        String number = buy();
        store.orderAdmin.changeStatus(number, OrderStatus.SHIPPED);
        assertThatThrownBy(() -> review(5)).as("shipped is not delivered").isInstanceOf(AccessDeniedException.class);
        store.orderAdmin.changeStatus(number, OrderStatus.DELIVERED);

        assertThat(review(4).authorName()).isEqualTo("Ayşe Y.");

        assertThat(store.catalog.book(dune.id()).averageRating()).contains(new BigDecimal("4.0"));
        assertThat(store.catalog.book(dune.id()).ratingCount()).isEqualTo(1);
        assertThat(store.reviews.ratingDistribution(dune.id()).count(Rating.of(4))).isEqualTo(1);
    }

    @Test
    void deletingTheReviewRemovesTheRatingFromTheCatalog() {
        String number = buy();
        store.orderAdmin.changeStatus(number, OrderStatus.SHIPPED);
        store.orderAdmin.changeStatus(number, OrderStatus.DELIVERED);
        review(2);

        store.reviews.delete(ayse.id(), dune.id());

        assertThat(store.catalog.book(dune.id()).ratingCount()).isZero();
        assertThat(store.catalog.book(dune.id()).averageRating()).isEmpty();
    }

    private String buy() {
        store.cart.add(CartRef.customer(ayse.id()), dune.id(), 1);
        long addressId = ayse.defaultAddress().orElseThrow().id();
        return store.checkout.placeOrder(new PlaceOrderCommand(ayse.id(), addressId, PaymentMethod.CREDIT_CARD))
                .orderNumber();
    }

    private com.bookstore.review.application.ReviewView review(int stars) {
        return store.reviews.write(new ReviewCommand(ayse.id(), dune.id(), stars, null));
    }
}
