package com.bookstore.review.domain;

import com.bookstore.shared.kernel.validation.Guard;
import com.bookstore.shared.kernel.domain.Entity;

import java.time.Clock;
import java.time.Instant;

/**
 * A customer's rating of a book, with an optional comment. A customer reviews a book at most once
 * and may later edit or delete the review.
 *
 * <p>Whether the customer is allowed to review the book (they must have received it) is checked by
 * the application layer, because it needs the customer's orders.
 */
public class Review extends Entity {

    /** Longest allowed comment. */
    public static final int MAX_COMMENT_LENGTH = 2000;

    private final long bookId;
    private final long customerId;
    private Rating rating;
    private String comment;
    private final Instant createdAt;
    private Instant updatedAt;

    private Review(long bookId, long customerId, Rating rating, String comment, Instant createdAt,
                   Instant updatedAt) {
        Guard.require(bookId > 0, "bookId", "must be a stored book");
        Guard.require(customerId > 0, "customerId", "must be a stored customer");
        this.bookId = bookId;
        this.customerId = customerId;
        this.rating = Guard.requireNonNull(rating, "rating");
        this.comment = validComment(comment);
        this.createdAt = Guard.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Guard.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Writes a new review.
     *
     * @param bookId     the reviewed book
     * @param customerId the author of the review
     * @param rating     the rating
     * @param comment    the comment, may be {@code null} or blank
     * @param clock      the clock giving the creation time
     * @return the review, not yet stored
     */
    public static Review write(long bookId, long customerId, Rating rating, String comment, Clock clock) {
        Instant now = Instant.now(clock);
        return new Review(bookId, customerId, rating, comment, now, now);
    }

    /**
     * Restores a review loaded from storage.
     *
     * @param id         the stored id
     * @param version    the stored version
     * @param bookId     the reviewed book
     * @param customerId the author
     * @param rating     the rating
     * @param comment    the comment
     * @param createdAt  when it was written
     * @param updatedAt  when it was last edited
     * @return the review
     */
    public static Review restore(long id, int version, long bookId, long customerId, Rating rating, String comment,
                                 Instant createdAt, Instant updatedAt) {
        Review review = new Review(bookId, customerId, rating, comment, createdAt, updatedAt);
        review.markPersisted(id, version);
        return review;
    }

    /** @return the reviewed book's id */
    public long bookId() {
        return bookId;
    }

    /** @return the author's customer id */
    public long customerId() {
        return customerId;
    }

    /** @return the rating */
    public Rating rating() {
        return rating;
    }

    /** @return the comment, or an empty string */
    public String comment() {
        return comment;
    }

    /** @return when the review was written */
    public Instant createdAt() {
        return createdAt;
    }

    /** @return when the review was last edited */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Tells whether a customer wrote this review.
     *
     * @param someCustomerId the customer's id
     * @return {@code true} for the author
     */
    public boolean isWrittenBy(long someCustomerId) {
        return customerId == someCustomerId;
    }

    /**
     * Changes the rating and comment.
     *
     * @param newRating  the new rating
     * @param newComment the new comment, may be {@code null} or blank
     * @param clock      the clock giving the edit time
     */
    public void edit(Rating newRating, String newComment, Clock clock) {
        Rating validRating = Guard.requireNonNull(newRating, "rating");
        String validComment = validComment(newComment);
        this.rating = validRating;
        this.comment = validComment;
        this.updatedAt = Instant.now(clock);
    }

    private static String validComment(String comment) {
        return Guard.optionalText(comment, "comment", MAX_COMMENT_LENGTH);
    }
}
