package com.bookstore.catalog.application;

import com.bookstore.catalog.domain.AuthorId;
import com.bookstore.catalog.domain.BookSort;
import com.bookstore.catalog.domain.CategoryId;
import com.bookstore.catalog.domain.Language;
import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.kernel.validation.Guard;

/**
 * A customer's catalog search as it comes from the search form. It names one category; the
 * service widens it to the category and its subcategories (CAT-5) and turns the query into a
 * {@code Specification<Book>} built from {@link com.bookstore.catalog.domain.BookSpecifications}.
 *
 * <p>A part the customer left empty is {@code null}: {@code Optional} is only a return type
 * (ADR-0004).
 *
 * @param text        words to find in the title, an author's name or the ISBN; may be {@code null}
 * @param categoryId  the chosen category; may be {@code null}
 * @param authorId    the chosen author; may be {@code null}
 * @param minPrice    lowest price; may be {@code null}
 * @param maxPrice    highest price; may be {@code null}
 * @param language    required language; may be {@code null}
 * @param inStockOnly whether to leave out books with no stock
 * @param sort        the order of the results
 */
public record BookSearchQuery(String text, CategoryId categoryId, AuthorId authorId, Money minPrice, Money maxPrice,
                              Language language, boolean inStockOnly, BookSort sort) {

    /** Rejects a missing order and an inverted price range. */
    public BookSearchQuery {
        Guard.requireNonNull(sort, "sort");
        if (minPrice != null && maxPrice != null) {
            Guard.require(maxPrice.isAtLeast(minPrice), "maxPrice", "must not be below the minimum price");
        }
    }

    /**
     * Returns a query that lists every book in the default order.
     *
     * @return the query
     */
    public static BookSearchQuery everything() {
        return new BookSearchQuery(null, null, null, null, null, null, false, BookSort.DEFAULT);
    }

    /**
     * Returns a copy that searches for some text.
     *
     * @param value the text, may be {@code null}
     * @return the new query
     */
    public BookSearchQuery withText(String value) {
        return new BookSearchQuery(value, categoryId, authorId, minPrice, maxPrice, language, inStockOnly, sort);
    }

    /**
     * Returns a copy limited to a category and its subcategories.
     *
     * @param value the category's id
     * @return the new query
     */
    public BookSearchQuery inCategory(CategoryId value) {
        return new BookSearchQuery(text, value, authorId, minPrice, maxPrice, language, inStockOnly, sort);
    }

    /**
     * Returns a copy limited to one author's books.
     *
     * @param value the author's id
     * @return the new query
     */
    public BookSearchQuery byAuthor(AuthorId value) {
        return new BookSearchQuery(text, categoryId, value, minPrice, maxPrice, language, inStockOnly, sort);
    }

    /**
     * Returns a copy limited to a price range.
     *
     * @param min lowest price, may be {@code null}
     * @param max highest price, may be {@code null}
     * @return the new query
     */
    public BookSearchQuery priced(Money min, Money max) {
        return new BookSearchQuery(text, categoryId, authorId, min, max, language, inStockOnly, sort);
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
