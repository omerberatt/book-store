package com.bookstore.catalog.persistence;

import com.bookstore.shared.kernel.paging.SortOrder;
import com.bookstore.shared.kernel.text.TextNormalizer;
import com.bookstore.catalog.domain.BookSearchCriteria;
import com.bookstore.shared.jdbc.JdbcExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Translates {@link BookSearchCriteria} into the {@code WHERE} and {@code ORDER BY} parts of a
 * query on {@code books b}. It follows the same rules as {@link BookSearchCriteria#asPredicate()}
 * and {@link com.bookstore.catalog.domain.BookSort#comparator()}, so the database and the
 * in-memory test repositories return the same books in the same order.
 *
 * <p>Only placeholders go into the SQL text; every value goes into {@link #params()}.
 */
final class BookSearchSql {

    private static final Pattern ISBN_FRAGMENT = Pattern.compile("\\d{4,13}");

    private final List<String> conditions = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private final String orderBy;

    BookSearchSql(BookSearchCriteria criteria) {
        if (!criteria.includeInactive()) {
            conditions.add("b.active = TRUE");
        }
        if (criteria.inStockOnly()) {
            conditions.add("b.stock > 0");
        }
        if (!criteria.categoryIds().isEmpty()) {
            conditions.add("b.category_id IN (" + JdbcExecutor.placeholders(criteria.categoryIds().size()) + ")");
            params.addAll(criteria.categoryIds());
        }
        criteria.authorId().ifPresent(authorId -> {
            conditions.add("EXISTS (SELECT 1 FROM book_authors ba WHERE ba.book_id = b.id AND ba.author_id = ?)");
            params.add(authorId);
        });
        criteria.minPrice().ifPresent(min -> {
            conditions.add("b.price >= ?");
            params.add(min);
        });
        criteria.maxPrice().ifPresent(max -> {
            conditions.add("b.price <= ?");
            params.add(max);
        });
        criteria.language().ifPresent(language -> {
            conditions.add("b.language = ?");
            params.add(language.code());
        });
        criteria.text().ifPresent(this::addTextCondition);
        orderBy = orderBy(criteria);
    }

    /** @return {@code " WHERE ..."} or an empty string */
    String where() {
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /** @return {@code " ORDER BY ..."} */
    String orderBy() {
        return orderBy;
    }

    /** @return the values for the placeholders in {@link #where()}, in order */
    List<Object> params() {
        return List.copyOf(params);
    }

    private void addTextCondition(String text) {
        String pattern = "%" + escapeLike(TextNormalizer.searchKey(text)) + "%";
        StringBuilder condition = new StringBuilder("(b.title_key LIKE ? ESCAPE '!'"
                + " OR EXISTS (SELECT 1 FROM book_authors ba JOIN authors a ON a.id = ba.author_id"
                + " WHERE ba.book_id = b.id AND a.name_key LIKE ? ESCAPE '!')");
        params.add(pattern);
        params.add(pattern);

        String digits = text.replace("-", "").replace(" ", "");
        if (ISBN_FRAGMENT.matcher(digits).matches()) {
            condition.append(" OR b.isbn LIKE ?");
            params.add("%" + digits + "%");
        }
        conditions.add(condition.append(")").toString());
    }

    /** Makes {@code %} and {@code _} in the search text match literally. */
    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String orderBy(BookSearchCriteria criteria) {
        String direction = criteria.sort().order() == SortOrder.ASC ? "ASC" : "DESC";
        String field = switch (criteria.sort().field()) {
            case TITLE -> "b.title_key " + direction;
            case PRICE -> "b.price " + direction;
            // Books without a year come first in ascending order, as in the domain comparator.
            case PUBLICATION_YEAR -> "CASE WHEN b.publication_year IS NULL THEN 0 ELSE 1 END " + direction
                    + ", b.publication_year " + direction;
            case RATING -> "CASE WHEN b.rating_count = 0 THEN 0 ELSE b.rating_sum * 1.0 / b.rating_count END "
                    + direction;
        };
        return " ORDER BY " + field + ", b.title_key ASC, b.isbn ASC";
    }
}
