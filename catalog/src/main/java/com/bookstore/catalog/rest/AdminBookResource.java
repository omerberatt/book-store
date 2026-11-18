package com.bookstore.catalog.rest;

import com.bookstore.catalog.api.AdminBookRow;
import com.bookstore.catalog.api.BookDetails;
import com.bookstore.catalog.api.CatalogAdminFacade;
import com.bookstore.catalog.api.CatalogStats;
import com.bookstore.catalog.rest.CatalogAdminRequests.ActiveRequest;
import com.bookstore.catalog.rest.CatalogAdminRequests.BookRequest;
import com.bookstore.catalog.rest.CatalogAdminRequests.PriceRequest;
import com.bookstore.catalog.rest.CatalogAdminRequests.StockRequest;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.web.Callers;
import com.bookstore.shared.web.PageResponse;
import com.bookstore.shared.web.Params;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Managing books (ADM-1, ADM-3, ADM-8). Every endpoint is for {@code ADMIN}: anonymous callers get
 * 401, customers 403 (ACC-6).
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/v1/admin/books}</td><td>every book, deactivated ones too; {@code ?q=} filters</td></tr>
 *   <tr><td>{@code POST /api/v1/admin/books}</td><td>add a book</td></tr>
 *   <tr><td>{@code GET /api/v1/admin/books/stats}</td><td>counts and books running low; {@code ?lowStockBelow=}</td></tr>
 *   <tr><td>{@code GET|PUT /api/v1/admin/books/{id}}</td><td>a book; change it</td></tr>
 *   <tr><td>{@code PUT /api/v1/admin/books/{id}/price}</td><td>change the price</td></tr>
 *   <tr><td>{@code PUT /api/v1/admin/books/{id}/stock}</td><td>set the stock</td></tr>
 *   <tr><td>{@code PUT /api/v1/admin/books/{id}/active}</td><td>take off sale or put back</td></tr>
 * </table>
 */
@Path("admin/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminBookResource {

    /** Books on sale with fewer copies than this run low, unless the request says otherwise. */
    static final int DEFAULT_LOW_STOCK = 5;

    private final CatalogAdminFacade catalog;

    /**
     * Creates the resource.
     *
     * @param catalog the catalog management use cases
     */
    @Inject
    public AdminBookResource(CatalogAdminFacade catalog) {
        this.catalog = catalog;
    }

    /** For the CDI container's client proxy only; never called by the application. */
    protected AdminBookResource() {
        this(null);
    }

    /**
     * Lists books.
     *
     * @param security the request's security
     * @param text     a fragment of the title, an author or the ISBN
     * @param page     the page number
     * @param size     the page size
     * @return the books
     */
    @GET
    public PageResponse<AdminBookRow> list(@Context SecurityContext security, @QueryParam("q") String text,
                                           @QueryParam("page") String page, @QueryParam("size") String size) {
        Callers.administrator(security);
        return PageResponse.of(catalog.books(Params.optional(text), PageResponse.pageOf(page, size)));
    }

    /**
     * Adds a book.
     *
     * @param security the request's security
     * @param book     the book
     * @return 201 with the book
     */
    @POST
    public Response create(@Context SecurityContext security, @Valid @NotNull BookRequest book) {
        Callers.administrator(security);
        BookDetails created = catalog.createBook(book.toCommand());
        return Response.created(URI.create("admin/books/" + created.id().value())).entity(created).build();
    }

    /**
     * Counts the books for the overview.
     *
     * @param security      the request's security
     * @param lowStockBelow the threshold for books running low
     * @return the counts
     */
    @GET
    @Path("stats")
    public CatalogStats stats(@Context SecurityContext security, @QueryParam("lowStockBelow") String lowStockBelow) {
        Callers.administrator(security);
        return catalog.stats(Params.integer("lowStockBelow", lowStockBelow, DEFAULT_LOW_STOCK));
    }

    /** @param security the request's security @param id the book @return the book, even off sale */
    @GET
    @Path("{id}")
    public BookDetails one(@Context SecurityContext security, @PathParam("id") String id) {
        Callers.administrator(security);
        return catalog.book(book(id));
    }

    /** @param security the request's security @param id the book @param book the new values @return the book */
    @PUT
    @Path("{id}")
    public BookDetails update(@Context SecurityContext security, @PathParam("id") String id,
                              @Valid @NotNull BookRequest book) {
        Callers.administrator(security);
        return catalog.updateBook(book(id), book.toCommand());
    }

    /** @param security the request's security @param id the book @param change the price @return the book */
    @PUT
    @Path("{id}/price")
    public BookDetails price(@Context SecurityContext security, @PathParam("id") String id,
                             @Valid @NotNull PriceRequest change) {
        Callers.administrator(security);
        return catalog.changePrice(book(id), Money.of(change.price()));
    }

    /** @param security the request's security @param id the book @param change the stock @return the book */
    @PUT
    @Path("{id}/stock")
    public BookDetails stock(@Context SecurityContext security, @PathParam("id") String id,
                             @Valid @NotNull StockRequest change) {
        Callers.administrator(security);
        return catalog.changeStock(book(id), change.stock());
    }

    /** @param security the request's security @param id the book @param change on or off sale @return the book */
    @PUT
    @Path("{id}/active")
    public BookDetails active(@Context SecurityContext security, @PathParam("id") String id,
                              @Valid @NotNull ActiveRequest change) {
        Callers.administrator(security);
        return catalog.setActive(book(id), change.active());
    }

    private static BookId book(String id) {
        return BookId.of(Params.id("id", id));
    }
}
