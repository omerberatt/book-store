package com.bookstore.catalog.domain;

import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.kernel.text.TextNormalizer;
import com.bookstore.shared.kernel.validation.Guard;

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
 * <p>A final class rather than a record: the optional parts are kept in nullable fields and
 * handed out through {@link Optional} accessors, because {@code Optional} is only a return type
 * (ADR-0004).
 */
public final class BookSearchCriteria {

    /** Longest accepted search text. */
    public static final int MAX_TEXT_LENGTH = 200;

    /** At least four digits are needed before a query is also matched against ISBNs. */
    private static final Pattern ISBN_FRAGMENT = Pattern.compile("\\d{4,13}");

    private final String text;
    private final Set<Long> categoryIds;
    private final Long authorId;
    private final Money minPrice;
    private final Money maxPrice;
    private final Language language;
    private final boolean inStockOnly;
    private final boolean includeInactive;
    private final BookSort sort;

    /** Normalises the text, copies the category ids and checks the price range. */
    private BookSearchCriteria(Builder builder) {
        String stripped = builder.text == null ? "" : builder.text.strip();
        this.text = stripped.isEmpty() ? null : Guard.requireMaxLength(stripped, "text", MAX_TEXT_LENGTH);
        this.categoryIds = Set.copyOf(builder.categoryIds);
        this.authorId = builder.authorId;
        this.minPrice = builder.minPrice;
        this.maxPrice = builder.maxPrice;
        this.language = builder.language;
        this.inStockOnly = builder.inStockOnly;
        this.includeInactive = builder.includeInactive;
        this.sort = Guard.requireNonNull(builder.sort, "sort");
        if (minPrice != null && maxPrice != null) {
            Guard.require(maxPrice.isAtLeast(minPrice), "maxPrice", "must not be below the minimum price");
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

    /** @return words to find in the title, an author's name or the ISBN */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /** @return categories to include; empty means every category */
    public Set<Long> categoryIds() {
        return categoryIds;
    }

    /** @return only books by this author */
    public Optional<Long> authorId() {
        return Optional.ofNullable(authorId);
    }

    /** @return lowest price, inclusive */
    public Optional<Money> minPrice() {
        return Optional.ofNullable(minPrice);
    }

    /** @return highest price, inclusive */
    public Optional<Money> maxPrice() {
        return Optional.ofNullable(maxPrice);
    }

    /** @return required language */
    public Optional<Language> language() {
        return Optional.ofNullable(language);
    }

    /** @return whether to leave out books with no stock */
    public boolean inStockOnly() {
        return inStockOnly;
    }

    /** @return whether to include deactivated books (administrators only) */
    public boolean includeInactive() {
        return includeInactive;
    }

    /** @return the order of the results */
    public BookSort sort() {
        return sort;
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
        if (authorId != null) {
            long required = authorId;
            predicate = predicate.and(book -> book.authors().stream()
                    .anyMatch(author -> !author.isNew() && author.id() == required));
        }
        if (minPrice != null) {
            Money min = minPrice;
            predicate = predicate.and(book -> book.price().isAtLeast(min));
        }
        if (maxPrice != null) {
            Money max = maxPrice;
            predicate = predicate.and(book -> max.isAtLeast(book.price()));
        }
        if (language != null) {
            Language required = language;
            predicate = predicate.and(book -> book.language() == required);
        }
        if (text != null) {
            predicate = predicate.and(matchesText(text));
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
            return new BookSearchCriteria(this);
        }
    }
}
