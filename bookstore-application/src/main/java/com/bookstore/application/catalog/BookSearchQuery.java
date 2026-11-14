package com.bookstore.application.catalog;

import com.bookstore.common.money.Money;
import com.bookstore.common.validation.Guard;
import com.bookstore.domain.catalog.BookSort;
import com.bookstore.domain.catalog.Language;

import java.util.Optional;

/**
 * A customer's catalog search as it comes from the search form. Unlike
 * {@link com.bookstore.domain.catalog.BookSearchCriteria}, it names one category; the service
 * widens it to the category and its subcategories (CAT-5).
 *
 * @param text        words to find in the title, an author's name or the ISBN
 * @param categoryId  the chosen category
 * @param authorId    the chosen author
 * @param minPrice    lowest price
 * @param maxPrice    highest price
 * @param language    required language
 * @param inStockOnly whether to leave out books with no stock
 * @param sort        the order of the results
 */
public record BookSearchQuery(Optional<String> text, Optional<Long> categoryId, Optional<Long> authorId,
                              Optional<Money> minPrice, Optional<Money> maxPrice, Optional<Language> language,
                              boolean inStockOnly, BookSort sort) {

    /** Rejects missing parts. */
    public BookSearchQuery {
        Guard.requireNonNull(text, "text");
        Guard.requireNonNull(categoryId, "categoryId");
        Guard.requireNonNull(authorId, "authorId");
        Guard.requireNonNull(minPrice, "minPrice");
        Guard.requireNonNull(maxPrice, "maxPrice");
        Guard.requireNonNull(language, "language");
        Guard.requireNonNull(sort, "sort");
    }

    /**
     * Returns a query that lists every book in the default order.
     *
     * @return the query
     */
    public static BookSearchQuery everything() {
        return new BookSearchQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), false, BookSort.DEFAULT);
    }

    /**
     * Returns a copy that searches for some text.
     *
     * @param value the text
     * @return the new query
     */
    public BookSearchQuery withText(String value) {
        return new BookSearchQuery(Optional.ofNullable(value), categoryId, authorId, minPrice, maxPrice, language,
                inStockOnly, sort);
    }

    /**
     * Returns a copy limited to a category and its subcategories.
     *
     * @param value the category's id
     * @return the new query
     */
    public BookSearchQuery inCategory(long value) {
        return new BookSearchQuery(text, Optional.of(value), authorId, minPrice, maxPrice, language, inStockOnly,
                sort);
    }

    /**
     * Returns a copy limited to one author's books.
     *
     * @param value the author's id
     * @return the new query
     */
    public BookSearchQuery byAuthor(long value) {
        return new BookSearchQuery(text, categoryId, Optional.of(value), minPrice, maxPrice, language, inStockOnly,
                sort);
    }

    /**
     * Returns a copy limited to a price range.
     *
     * @param min lowest price, may be {@code null}
     * @param max highest price, may be {@code null}
     * @return the new query
     */
    public BookSearchQuery priced(Money min, Money max) {
        return new BookSearchQuery(text, categoryId, authorId, Optional.ofNullable(min), Optional.ofNullable(max),
                language, inStockOnly, sort);
    }

    /**
     * Returns a copy limited to books in stock.
     *
     * @return the new query
     */
    public BookSearchQuery onlyInStock() {
        return new BookSearchQuery(text, categoryId, authorId, minPrice, maxPrice, language, true, sort);
    }

    /**
     * Returns a copy with another order.
     *
     * @param value the order
     * @return the new query
     */
    public BookSearchQuery sortedBy(BookSort value) {
        return new BookSearchQuery(text, categoryId, authorId, minPrice, maxPrice, language, inStockOnly, value);
    }
}
