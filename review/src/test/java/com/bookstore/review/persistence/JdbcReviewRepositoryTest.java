package com.bookstore.review.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.persistence.CatalogDb;
import com.bookstore.customer.domain.Customer;
import com.bookstore.customer.persistence.CustomerDb;
import com.bookstore.review.domain.Rating;
import com.bookstore.review.domain.Review;
import com.bookstore.shared.jdbc.DataAccessException;
import com.bookstore.shared.jdbc.TestDatabase;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.testing.TestClock;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class JdbcReviewRepositoryTest {

    private final TestDatabase database = TestDatabase.withModules("catalog", "customer", "sales", "review");
    private final JdbcReviewRepository reviews = new JdbcReviewRepository(database.jdbc());
    private final CustomerDb customers = new CustomerDb(database);
    private final Customer ayse = customers.customer("ayse@example.com");
    private final Customer mehmet = customers.customer("mehmet@example.com");
    private final Book dune = new CatalogDb(database).book("Dune", "100", 10);

    @Test
    void reviewSurvivesTheRoundTripAndEdits() {
        Review review = reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(4), "Güzel", TestClock.CLOCK));

        Review loaded = reviews.findByBookIdAndCustomerId(dune.id(), ayse.id()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(review.id());
        assertThat(loaded.rating()).isEqualTo(Rating.of(4));
        assertThat(loaded.comment()).isEqualTo("Güzel");
        assertThat(loaded.createdAt()).isEqualTo(TestClock.NOW);

        loaded.edit(Rating.of(5), null, Clock.offset(TestClock.CLOCK, Duration.ofDays(1)));
        reviews.save(loaded);
        Review edited = reviews.findById(review.id()).orElseThrow();
        assertThat(edited.rating()).isEqualTo(Rating.of(5));
        assertThat(edited.comment()).isEmpty();
        assertThat(edited.updatedAt()).isAfter(edited.createdAt());
    }

    @Test
    void oneReviewPerCustomerAndBook() {
        reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(4), null, TestClock.CLOCK));

        assertThatThrownBy(() -> reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(1), null, TestClock.CLOCK)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("review.duplicate"));
    }

    @Test
    void listsRatingsAndDeletes() {
        Review first = reviews.save(Review.write(dune.id(), ayse.id(), Rating.of(5), null, TestClock.CLOCK));
        reviews.save(Review.write(dune.id(), mehmet.id(), Rating.of(2), null,
                Clock.offset(TestClock.CLOCK, Duration.ofMinutes(1))));

        assertThat(reviews.findByBookId(dune.id(), PageRequest.first()).content())
                .extracting(Review::customerId).containsExactly(mehmet.id(), ayse.id());
        assertThat(reviews.findRatingsByBookId(dune.id())).containsExactlyInAnyOrder(Rating.of(5), Rating.of(2));
        assertThat(reviews.findAll(PageRequest.first()).totalElements()).isEqualTo(2);

        reviews.delete(first);
        assertThat(reviews.findRatingsByBookId(dune.id())).containsExactly(Rating.of(2));
    }

    @Test
    void ratingMustBeOneToFiveInTheDatabaseToo() {
        assertThatThrownBy(() -> database.jdbc().update("INSERT INTO reviews (book_id, user_id, rating, comment_text, "
                + "created_at, updated_at) VALUES (?, ?, 6, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                dune.id(), ayse.id()))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(e.isConstraintViolation()).isTrue());
    }
}
