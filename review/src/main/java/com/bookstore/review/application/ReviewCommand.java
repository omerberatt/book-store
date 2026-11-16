package com.bookstore.review.application;

/**
 * The review form, used both for writing and for editing a review.
 *
 * @param customerId the signed-in customer
 * @param bookId     the reviewed book
 * @param stars      the rating, from 1 to 5
 * @param comment    the comment, may be {@code null} or blank
 */
public record ReviewCommand(long customerId, long bookId, int stars, String comment) {
}
