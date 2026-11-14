package com.bookstore.application.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.application.Shop;
import com.bookstore.application.catalog.BookDetails;
import com.bookstore.common.error.AccessDeniedException;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.review.Rating;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    private final Shop shop = new Shop();
    private final Book dune = shop.book("Dune", "100.00", 10);
    private final long ayse = shop.customer("ayse@example.com");

    @Test
    void onlyCustomersWhoReceivedTheBookCanReviewIt() {
        assertThat(shop.reviewService.canReview(ayse, dune.id())).isFalse();
        assertThatThrownBy(() -> write(ayse, 5))
                .isInstanceOfSatisfying(AccessDeniedException.class,
                        e -> assertThat(e.code()).isEqualTo(ReviewService.NOT_ELIGIBLE));

        shop.buy(ayse, dune, 1);
        assertThat(shop.reviewService.canReview(ayse, dune.id())).as("paid but not delivered").isFalse();
    }

    @Test
    void writingAReviewUpdatesTheBooksRating() {
        shop.buyAndReceive(ayse, dune);
        assertThat(shop.reviewService.canReview(ayse, dune.id())).isTrue();

        ReviewView review = shop.reviewService.write(new ReviewCommand(ayse, dune.id(), 4, "Harika bir kitap"));

        assertThat(review.authorName()).isEqualTo("Ayşe Y.");
        assertThat(review.stars()).isEqualTo(4);
        assertThat(review.edited()).isFalse();
        BookDetails details = shop.catalog.book(dune.id());
        assertThat(details.averageRating()).contains(new BigDecimal("4.0"));
        assertThat(details.ratingCount()).isEqualTo(1);
        assertThat(details.distribution().count(Rating.of(4))).isEqualTo(1);
        assertThat(shop.reviewService.canReview(ayse, dune.id())).isFalse();
    }

    @Test
    void aCustomerReviewsABookOnlyOnce() {
        shop.buyAndReceive(ayse, dune);
        write(ayse, 4);

        assertThatThrownBy(() -> write(ayse, 5))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ReviewService.DUPLICATE));
        assertThat(shop.catalog.book(dune.id()).ratingCount()).isEqualTo(1);
    }

    @Test
    void editingMovesTheRatingInTheTotals() {
        shop.buyAndReceive(ayse, dune);
        write(ayse, 2);

        ReviewView edited = shop.reviewService.edit(new ReviewCommand(ayse, dune.id(), 5, "Fikrim değişti"));

        assertThat(edited.stars()).isEqualTo(5);
        assertThat(edited.comment()).isEqualTo("Fikrim değişti");
        assertThat(shop.catalog.book(dune.id()).averageRating()).contains(new BigDecimal("5.0"));
        assertThat(shop.reviewService.myReview(ayse, dune.id())).map(ReviewView::stars).contains(5);
    }

    @Test
    void deletingRemovesTheRatingFromTheTotals() {
        shop.buyAndReceive(ayse, dune);
        write(ayse, 3);

        shop.reviewService.delete(ayse, dune.id());

        assertThat(shop.catalog.book(dune.id()).ratingCount()).isZero();
        assertThat(shop.reviewService.myReview(ayse, dune.id())).isEmpty();
        assertThat(shop.reviewService.canReview(ayse, dune.id())).as("may review again").isTrue();
        assertThatThrownBy(() -> shop.reviewService.delete(ayse, dune.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void reviewsAreListedNewestFirstWithPublicNames() {
        long mehmet = shop.customer("mehmet@example.com");
        shop.accounts.updateProfile(mehmet, "Mehmet", "Öztürk", "mehmet@example.com");
        shop.buyAndReceive(ayse, dune);
        shop.buyAndReceive(mehmet, dune);
        write(ayse, 5);
        write(mehmet, 3);

        Page<ReviewView> page = shop.reviewService.reviewsOf(dune.id(), PageRequest.first());

        assertThat(page.content()).extracting(ReviewView::authorName).containsExactly("Mehmet Ö.", "Ayşe Y.");
        assertThat(shop.catalog.book(dune.id()).averageRating()).contains(new BigDecimal("4.0"));
    }

    @Test
    void invalidRatingIsRejectedBeforeAnythingHappens() {
        shop.buyAndReceive(ayse, dune);
        int before = shop.transactions.started();

        assertThatThrownBy(() -> write(ayse, 6)).hasMessageContaining("rating");
        assertThat(shop.transactions.started()).isEqualTo(before);
    }

    private ReviewView write(long customerId, int stars) {
        return shop.reviewService.write(new ReviewCommand(customerId, dune.id(), stars, null));
    }
}
