package com.bookstore.application.catalog;

import com.bookstore.common.money.Money;
import com.bookstore.domain.catalog.Author;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.Language;
import com.bookstore.domain.review.RatingDistribution;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * Everything a book's detail page shows.
 *
 * @param id              the book's id
 * @param isbn            the ISBN-13
 * @param title           the title
 * @param authors         the authors, in order
 * @param categoryId      the category's id
 * @param categoryPath    the category names from the root down, for breadcrumbs
 * @param publisher       the publisher, or an empty string
 * @param publicationYear the year of publication, if known
 * @param language        the language
 * @param pageCount       the number of pages, zero if unknown
 * @param description     the description, or an empty string
 * @param price           the current price
 * @param stock           copies available
 * @param inStock         whether at least one copy can be ordered
 * @param averageRating   the average rating, if anyone rated the book
 * @param ratingCount     the number of ratings
 * @param distribution    how many ratings of each star value the book received
 * @param coverImage      the storage key of the cover image, if any
 */
public record BookDetails(long id, String isbn, String title, List<AuthorRef> authors, long categoryId,
                          List<String> categoryPath, String publisher, Optional<Year> publicationYear,
                          Language language, int pageCount, String description, Money price, int stock,
                          boolean inStock, Optional<BigDecimal> averageRating, int ratingCount,
                          RatingDistribution distribution, Optional<String> coverImage) {

    /** Copies the lists. */
    public BookDetails {
        authors = List.copyOf(authors);
        categoryPath = List.copyOf(categoryPath);
    }

    /**
     * Copies a book's details.
     *
     * @param book         a stored book
     * @param distribution the book's rating distribution
     * @return the details
     */
    public static BookDetails of(Book book, RatingDistribution distribution) {
        List<AuthorRef> authors = book.authors().stream().map(AuthorRef::of).toList();
        return new BookDetails(book.id(), book.isbn().value(), book.title(), authors, book.category().id(),
                book.category().path(), book.publisher(), book.publicationYear(), book.language(), book.pageCount(),
                book.description(), book.price(), book.stock().quantity(), book.isAvailableForSale(),
                book.ratings().average(), book.ratings().count(), distribution, book.coverImage());
    }

    /**
     * An author, with the id needed to link to the author's other books.
     *
     * @param id   the author's id
     * @param name the author's name
     */
    public record AuthorRef(long id, String name) {

        /**
         * Copies an author.
         *
         * @param author a stored author
         * @return the reference
         */
        public static AuthorRef of(Author author) {
            return new AuthorRef(author.id(), author.name());
        }
    }
}
