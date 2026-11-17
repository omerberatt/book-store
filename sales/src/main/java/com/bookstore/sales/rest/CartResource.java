package com.bookstore.sales.rest;

import com.bookstore.sales.api.CartFacade;
import com.bookstore.sales.api.CartRef;
import com.bookstore.sales.api.CartView;
import com.bookstore.sales.rest.SalesRequests.AddToCartRequest;
import com.bookstore.sales.rest.SalesRequests.QuantityRequest;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.web.Routes;
import com.bookstore.shared.web.WebRequest;
import com.bookstore.shared.web.WebResponse;

import java.util.Objects;

/**
 * The signed-in customer's cart (CRT-1, CRT-3 – CRT-5). Every answer is the whole cart, checked
 * against the catalog. A visitor's cart is not served over HTTP in Phase 5 (ADR-0019).
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/v1/cart}</td><td>the cart</td></tr>
 *   <tr><td>{@code DELETE /api/v1/cart}</td><td>empty it</td></tr>
 *   <tr><td>{@code POST /api/v1/cart/items}</td><td>add copies of a book</td></tr>
 *   <tr><td>{@code PUT /api/v1/cart/items/{bookId}}</td><td>set the quantity; zero removes</td></tr>
 *   <tr><td>{@code DELETE /api/v1/cart/items/{bookId}}</td><td>remove a book</td></tr>
 * </table>
 */
public final class CartResource implements Routes.Resource {

    private final CartFacade cart;

    /**
     * Creates the resource.
     *
     * @param cart the cart use cases
     */
    public CartResource(CartFacade cart) {
        this.cart = Objects.requireNonNull(cart, "cart");
    }

    @Override
    public void register(Routes routes) {
        routes.get("/api/v1/cart", request -> answer(cart.view(mine(request))))
                .delete("/api/v1/cart", request -> answer(cart.clear(mine(request))))
                .post("/api/v1/cart/items", this::add)
                .put("/api/v1/cart/items/{bookId}", this::changeQuantity)
                .delete("/api/v1/cart/items/{bookId}",
                        request -> answer(cart.remove(mine(request), BookId.of(request.pathId("bookId")))));
    }

    private WebResponse add(WebRequest request) {
        AddToCartRequest add = request.body(AddToCartRequest.class);
        return answer(cart.add(mine(request), BookId.of(add.bookId()), add.quantity()));
    }

    private WebResponse changeQuantity(WebRequest request) {
        QuantityRequest change = request.body(QuantityRequest.class);
        return answer(cart.changeQuantity(mine(request), BookId.of(request.pathId("bookId")), change.quantity()));
    }

    private static CartRef mine(WebRequest request) {
        return CartRef.customer(request.customer().customerId());
    }

    private static WebResponse answer(CartView view) {
        return WebResponse.ok(CartResponse.of(view));
    }
}
