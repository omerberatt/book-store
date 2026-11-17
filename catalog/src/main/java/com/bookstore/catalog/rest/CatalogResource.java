package com.bookstore.catalog.rest;

import com.bookstore.catalog.api.BookSearchQuery;
import com.bookstore.catalog.api.CatalogFacade;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.kernel.paging.SortOrder;
import com.bookstore.shared.web.PageResponse;
import com.bookstore.shared.web.Routes;
import com.bookstore.shared.web.WebException;
import com.bookstore.shared.web.WebRequest;
import com.bookstore.shared.web.WebResponse;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * The catalog's HTTP endpoints (ADR-0009). Everyone may use them; no sign-in is needed.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/v1/books}</td><td>search; query parameters {@code q}, {@code category},
 *       {@code author}, {@code minPrice}, {@code maxPrice}, {@code language}, {@code inStock},
 *       {@code sort} ({@code title}, {@code price}, {@code publication_year}, {@code rating}),
 *       {@code order} ({@code asc}, {@code desc}), {@code page}, {@code size}</td></tr>
 *   <tr><td>{@code GET /api/v1/books/{id}}</td><td>a book's details</td></tr>
 *   <tr><td>{@code GET /api/v1/categories}</td><td>the category tree</td></tr>
 * </table>
 */
public final class CatalogResource implements Routes.Resource {

    private final CatalogFacade catalog;

    /**
     * Creates the resource.
     *
     * @param catalog the catalog's use cases
     */
    public CatalogResource(CatalogFacade catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public void register(Routes routes) {
        routes.get("/api/v1/books", this::search)
                .get("/api/v1/books/{id}", this::book)
                .get("/api/v1/categories", request -> WebResponse.ok(catalog.categoryTree()));
    }

    private WebResponse search(WebRequest request) {
        BookSearchQuery query = BookSearchQuery.everything()
                .withText(request.query("q"))
                .priced(money(request, "minPrice"), money(request, "maxPrice"))
                .inLanguage(request.query("language"))
                .sortedBy(sortField(request), sortOrder(request));
        Long category = request.queryId("category");
        if (category != null) {
            query = query.inCategory(category);
        }
        Long author = request.queryId("author");
        if (author != null) {
            query = query.byAuthor(author);
        }
        if (request.queryFlag("inStock")) {
            query = query.onlyInStock();
        }
        return WebResponse.ok(PageResponse.of(catalog.search(query, PageResponse.pageOf(request))));
    }

    private WebResponse book(WebRequest request) {
        return WebResponse.ok(catalog.book(BookId.of(request.pathId("id"))));
    }

    private static Money money(WebRequest request, String name) {
        String value = request.query(name);
        if (value == null) {
            return null;
        }
        try {
            return Money.of(new BigDecimal(value));
        } catch (NumberFormatException e) {
            throw WebException.badRequest("request.bad-parameter", name + " must be an amount such as 120.50");
        }
    }

    private static String sortField(WebRequest request) {
        String sort = request.query("sort");
        return sort == null ? BookSearchQuery.DEFAULT_SORT : sort.toUpperCase(Locale.ROOT);
    }

    private static SortOrder sortOrder(WebRequest request) {
        String order = request.query("order");
        if (order == null) {
            return SortOrder.ASC;
        }
        return switch (order.toLowerCase(Locale.ROOT)) {
            case "asc" -> SortOrder.ASC;
            case "desc" -> SortOrder.DESC;
            default -> throw WebException.badRequest("request.bad-parameter", "order must be asc or desc");
        };
    }
}
