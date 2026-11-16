package com.bookstore.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.catalog.api.RatingFacade;
import com.bookstore.customer.api.CustomerDirectory;
import com.bookstore.review.domain.Rating;
import com.bookstore.review.domain.RatingDistribution;
import com.bookstore.review.persistence.InMemoryReviewRepository;
import com.bookstore.sales.api.PurchaseHistory;
import com.bookstore.shared.kernel.address.Address;
import com.bookstore.shared.kernel.error.AccessDeniedException;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.testing.DirectTransactions;
import com.bookstore.shared.kernel.testing.TestClock;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    private static final long DUNE = 1;
    private static final long AYSE = 10;
    private static final long MEHMET = 11;

    private final InMemoryReviewRepository reviews = new InMemoryReviewRepository();
    private final FakeRatings ratings = new FakeRatings(DUNE);
    private final FakePurchases purchases = new FakePurchases();
    private final FakeNames names = new FakeNames(Map.of(AYSE, "Ayşe Y.", MEHMET, "Mehmet Ö."));
    private final DirectTransactions transactions = new DirectTransactions();
    private final ReviewService service =
            new ReviewService(reviews, ratings, purchases, names, transactions, TestClock.CLOCK);

    @Test
    void onlyCustomersWhoReceivedTheBookCanReviewIt() {
        assertThat(service.canReview(AYSE, DUNE)).isFalse();
        assertThatThrownBy(() -> write(AYSE, 5))
                .isInstanceOfSatisfying(AccessDeniedException.class,
                        e -> assertThat(e.code()).isEqualTo(ReviewService.NOT_ELIGIBLE));
        assertThat(ratings.count(DUNE)).isZero();
    }

    @Test
    void unknownBookIsReported() {
        purchases.received(AYSE, 99);

        assertThatThrownBy(() -> service.write(new ReviewCommand(AYSE, 99, 4, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void writingAReviewUpdatesTheBooksRating() {
        purchases.received(AYSE, DUNE);
        assertThat(service.canReview(AYSE, DUNE)).isTrue();

        ReviewView review = service.write(new ReviewCommand(AYSE, DUNE, 4, "Harika bir kitap"));

        assertThat(review.authorName()).isEqualTo("Ayşe Y.");
        assertThat(review.stars()).isEqualTo(4);
        assertThat(review.edited()).isFalse();
        assertThat(ratings.count(DUNE)).isEqualTo(1);
        assertThat(ratings.sum(DUNE)).isEqualTo(4);
        assertThat(service.ratingDistribution(DUNE).count(Rating.of(4))).isEqualTo(1);
        assertThat(service.canReview(AYSE, DUNE)).isFalse();
    }

    @Test
    void aCustomerReviewsABookOnlyOnce() {
        purchases.received(AYSE, DUNE);
        write(AYSE, 4);

        assertThatThrownBy(() -> write(AYSE, 5))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ReviewService.DUPLICATE));
        assertThat(ratings.count(DUNE)).isEqualTo(1);
    }

    @Test
    void editingMovesTheRatingInTheTotals() {
        purchases.received(AYSE, DUNE);
        write(AYSE, 2);

        ReviewView edited = new ReviewService(reviews, ratings, purchases, names, transactions,
                Clock.offset(TestClock.CLOCK, Duration.ofDays(1)))
                .edit(new ReviewCommand(AYSE, DUNE, 5, "Fikrim değişti"));

        assertThat(edited.stars()).isEqualTo(5);
        assertThat(edited.comment()).isEqualTo("Fikrim değişti");
        assertThat(edited.edited()).isTrue();
        assertThat(ratings.count(DUNE)).isEqualTo(1);
        assertThat(ratings.sum(DUNE)).isEqualTo(5);
        assertThat(service.myReview(AYSE, DUNE)).map(ReviewView::stars).contains(5);
    }

    @Test
    void deletingRemovesTheRatingFromTheTotals() {
        purchases.received(AYSE, DUNE);
        write(AYSE, 3);

        service.delete(AYSE, DUNE);

        assertThat(ratings.count(DUNE)).isZero();
        assertThat(service.myReview(AYSE, DUNE)).isEmpty();
        assertThat(service.canReview(AYSE, DUNE)).as("may review again").isTrue();
        assertThatThrownBy(() -> service.delete(AYSE, DUNE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void reviewsAreListedNewestFirstWithPublicNames() {
        purchases.received(AYSE, DUNE);
        purchases.received(MEHMET, DUNE);
        write(AYSE, 5);
        write(MEHMET, 3);

        Page<ReviewView> page = service.reviewsOf(DUNE, PageRequest.first());

        assertThat(page.content()).extracting(ReviewView::authorName).containsExactly("Mehmet Ö.", "Ayşe Y.");
        RatingDistribution distribution = service.ratingDistribution(DUNE);
        assertThat(distribution.total()).isEqualTo(2);
        assertThat(distribution.average()).hasValueSatisfying(a -> assertThat(a).isEqualByComparingTo("4.0"));
    }

    @Test
    void invalidRatingIsRejectedBeforeAnythingHappens() {
        purchases.received(AYSE, DUNE);

        assertThatThrownBy(() -> write(AYSE, 6)).hasMessageContaining("rating");
        assertThat(transactions.started()).isZero();
    }

    private ReviewView write(long customerId, int stars) {
        return service.write(new ReviewCommand(customerId, DUNE, stars, null));
    }

    /** The catalog's rating totals, kept per book. */
    private static final class FakeRatings implements RatingFacade {

        private final Map<Long, int[]> totals = new HashMap<>();

        FakeRatings(long... bookIds) {
            for (long bookId : bookIds) {
                totals.put(bookId, new int[2]);
            }
        }

        int count(long bookId) {
            return totals.get(bookId)[0];
        }

        int sum(long bookId) {
            return totals.get(bookId)[1];
        }

        @Override
        public boolean exists(long bookId) {
            return totals.containsKey(bookId);
        }

        @Override
        public void addRating(long bookId, int stars) {
            int[] t = require(bookId);
            t[0]++;
            t[1] += stars;
        }

        @Override
        public void removeRating(long bookId, int stars) {
            int[] t = require(bookId);
            t[0]--;
            t[1] -= stars;
        }

        private int[] require(long bookId) {
            int[] t = totals.get(bookId);
            if (t == null) {
                throw new NotFoundException("Book", bookId);
            }
            return t;
        }
    }

    /** Which customer received which book. */
    private static final class FakePurchases implements PurchaseHistory {

        private final Set<List<Long>> received = new HashSet<>();

        void received(long customerId, long bookId) {
            received.add(List.of(customerId, bookId));
        }

        @Override
        public boolean hasReceived(long customerId, long bookId) {
            return received.contains(List.of(customerId, bookId));
        }
    }

    /** Fixed public names. */
    private record FakeNames(Map<Long, String> names) implements CustomerDirectory {

        @Override
        public Address shippingAddress(long customerId, long addressId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Long, String> publicNames(Collection<Long> customerIds) {
            return customerIds.stream().filter(names::containsKey).collect(Collectors.toMap(id -> id, names::get));
        }
    }
}
