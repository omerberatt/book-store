package com.bookstore.application.catalog;

import com.bookstore.common.money.Money;
import com.bookstore.domain.catalog.Book;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What a book list shows for each book. A read-only copy, so the web layer cannot change the
 * book by accident.
 *
 * @param id            the book's id
 * @param isbn          the ISBN-13
 * @param title         the title
 * @param authorNames   the authors, joined with commas
 * @param price         the current price
 * @param inStock       whether at least one copy can be ordered
 * @param averageRating the average rating with one decimal place, if anyone rated the book
 * @param ratingCount   the number of ratings
 * @param coverImage    the storage key of the cover image, if any
 */
public record BookSummary(long id, String isbn, String title, String authorNames, Money price, boolean inStock,
                          Optional<BigDecimal> averageRating, int ratingCount, Optional<String> coverImage) {

    /**
     * Copies the parts of a book that lists show.
     *
     * @param book a stored book
     * @return the summary
     */
    public static BookSummary of(Book book) {
        return new BookSummary(book.id(), book.isbn().value(), book.title(), book.authorNames(), book.price(),
                book.isAvailableForSale(), book.ratings().average(), book.ratings().count(), book.coverImage());
    }
}
