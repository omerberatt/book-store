package com.bookstore.review.application;

import com.bookstore.review.domain.ReviewId;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.review.domain.Review;

import java.time.Instant;

/**
 * A review as shown on a book's page.
 *
 * @param id         the review's id
 * @param bookId     the reviewed book
 * @param customerId the author's id, so a page can mark the viewer's own review
 * @param authorName the author's public name, for example {@code "Ayşe Y."}
 * @param stars      the rating, from 1 to 5
 * @param comment    the comment, or an empty string
 * @param createdAt  when it was written
 * @param edited     whether it was changed after it was written
 */
public record ReviewView(ReviewId id, BookId bookId, CustomerId customerId, String authorName, int stars, String comment,
                         Instant createdAt, boolean edited) {

    /**
     * Copies a review.
     *
     * @param review     a stored review
     * @param authorName the author's public name
     * @return the view
     */
    public static ReviewView of(Review review, String authorName) {
        return new ReviewView(review.id(), review.bookId(), review.customerId(), authorName, review.rating().stars(),
                review.comment(), review.createdAt(), review.updatedAt().isAfter(review.createdAt()));
    }
}
