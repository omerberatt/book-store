package com.bookstore.domain.catalog;

import com.bookstore.common.money.Money;
import com.bookstore.common.text.TextNormalizer;
import com.bookstore.common.validation.Guard;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * What a catalog search is looking for. Every part is optional; an empty criteria matches every
 * active book.
 *
 * <p>Build one with {@link #builder()}:
 *
 * <pre>{@code
 * BookSearchCriteria criteria = BookSearchCriteria.builder()
 *         .text("sabahattin")
 *         .maxPrice(Money.of("200"))
 *         .inStockOnly(true)
 *         .sort(new BookSort(BookSortField.PRICE, SortOrder.ASC))
 *         .build();
 * }</pre>
 *
 * @param text            words to find in the title, an author's name or the ISBN
 * @param categoryIds     categories to include; empty means every category
 * @param authorId        only books by this author
 * @param minPrice        lowest price, inclusive
 * @param maxPrice        highest price, inclusive
 * @param language        required language
 * @param inStockOnly     whether to leave out books with no stock
 * @param includeInactive whether to include deactivated books (administrators only)
 * @param sort            the order of the results
 */
public record BookSearchCriteria(
        Optional<String> text,
        Set<Long> categoryIds,
        Optional<Long> authorId,
        Optional<Money> minPrice,
        Optional<Money> maxPrice,
        Optional<Language> language,
        boolean inStockOnly,
        boolean includeInactive,
        BookSort sort) {

    /** Longest accepted search text. */
    public static final int MAX_TEXT_LENGTH = 200;

    /** At least four digits are needed before a query is also matched against ISBNs. */
    private static final Pattern ISBN_FRAGMENT = Pattern.compile("\\d{4,13}");

    /** Normalises the text, copies the category ids and checks the price range. */
    public BookSearchCriteria {
        text = Guard.requireNonNull(text, "text")
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .map(t -> Guard.requireMaxLength(t, "text", MAX_TEXT_LENGTH));
        categoryIds = Set.copyOf(Guard.requireNonNull(categoryIds, "categoryIds"));
        Guard.requireNonNull(authorId, "authorId");
        Guard.requireNonNull(minPrice, "minPrice");
        Guard.requireNonNull(maxPrice, "maxPrice");
        Guard.requireNonNull(language, "language");
        Guard.requireNonNull(sort, "sort");
        if (minPrice.isPresent() && maxPrice.isPresent()) {
            Guard.require(maxPrice.get().isAtLeast(minPrice.get()), "maxPrice", "must not be below the minimum price");
        }
    }

    /**
     * Returns criteria that match every active book, in the default order.
     *
     * @return the criteria
     */
    public static BookSearchCriteria all() {
        return builder().build();
    }

    /**
     * Starts building criteria.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Turns the criteria into a test that can be applied to a single book. Used by in-memory
     * repositories; the JDBC repository translates the same criteria into SQL instead.
     *
     * @return a predicate that accepts matching books
     */
    public Predicate<Book> asPredicate() {
        Predicate<Book> predicate = book -> includeInactive || book.isActive();
        if (inStockOnly) {
            predicate = predicate.and(book -> book.stock().isAvailable());
        }
        if (!categoryIds.isEmpty()) {
            predicate = predicate.and(book -> !book.category().isNew() && categoryIds.contains(book.category().id()));
        }
        if (authorId.isPresent()) {
            long required = authorId.get();
            predicate = predicate.and(book -> book.authors().stream()
                    .anyMatch(author -> !author.isNew() && author.id() == required));
        }
        if (minPrice.isPresent()) {
            Money min = minPrice.get();
            predicate = predicate.and(book -> book.price().isAtLeast(min));
        }
        if (maxPrice.isPresent()) {
            Money max = maxPrice.get();
            predicate = predicate.and(book -> max.isAtLeast(book.price()));
        }
        if (language.isPresent()) {
            Language required = language.get();
            predicate = predicate.and(book -> book.language() == required);
        }
        if (text.isPresent()) {
            predicate = predicate.and(matchesText(text.get()));
        }
        return predicate;
    }

    private static Predicate<Book> matchesText(String query) {
        String key = TextNormalizer.searchKey(query);
        String digits = query.replace("-", "").replace(" ", "");
        boolean looksLikeIsbn = ISBN_FRAGMENT.matcher(digits).matches();
        return book -> TextNormalizer.searchKey(book.title()).contains(key)
                || book.authors().stream().anyMatch(a -> TextNormalizer.searchKey(a.name()).contains(key))
                || (looksLikeIsbn && book.isbn().value().contains(digits));
    }

    /**
     * Collects optional search parts.
     */
    public static final class Builder {

        private String text;
        private final Set<Long> categoryIds = new HashSet<>();
        private Long authorId;
        private Money minPrice;
        private Money maxPrice;
        private Language language;
        private boolean inStockOnly;
        private boolean includeInactive;
        private BookSort sort = BookSort.DEFAULT;

        private Builder() {
        }

        /** @param value words to search for @return this builder */
        public Builder text(String value) {
            this.text = value;
            return this;
        }

        /** @param ids categories to include @return this builder */
        public Builder categoryIds(Set<Long> ids) {
            this.categoryIds.addAll(ids);
            return this;
        }

        /** @param value only books by this author @return this builder */
        public Builder authorId(long value) {
            this.authorId = value;
            return this;
        }

        /** @param value lowest price @return this builder */
        public Builder minPrice(Money value) {
            this.minPrice = value;
            return this;
        }

        /** @param value highest price @return this builder */
        public Builder maxPrice(Money value) {
            this.maxPrice = value;
            return this;
        }

        /** @param value required language @return this builder */
        public Builder language(Language value) {
            this.language = value;
            return this;
        }

        /** @param value whether to leave out books with no stock @return this builder */
        public Builder inStockOnly(boolean value) {
            this.inStockOnly = value;
            return this;
        }

        /** @param value whether to include deactivated books @return this builder */
        public Builder includeInactive(boolean value) {
            this.includeInactive = value;
            return this;
        }

        /** @param value the order of the results @return this builder */
        public Builder sort(BookSort value) {
            this.sort = value;
            return this;
        }

        /**
         * Validates and creates the criteria.
         *
         * @return the criteria
         */
        public BookSearchCriteria build() {
            return new BookSearchCriteria(Optional.ofNullable(text), categoryIds, Optional.ofNullable(authorId),
                    Optional.ofNullable(minPrice),
                    Optional.ofNullable(maxPrice), Optional.ofNullable(language), inStockOnly, includeInactive, sort);
        }
    }
}
