package com.bookstore.sales.rest;

import com.bookstore.sales.api.OrderAdminFacade;
import com.bookstore.sales.rest.SalesRequests.StatusChangeRequest;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.web.PageResponse;
import com.bookstore.shared.web.Routes;
import com.bookstore.shared.web.WebRequest;
import com.bookstore.shared.web.WebResponse;

import java.util.Objects;

/**
 * Order management for administrators (ORD-6, ORD-7, ADM-5). Every endpoint needs a signed-in
 * {@code ADMIN}: anonymous callers get 401, customers 403.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/v1/admin/orders}</td><td>all orders, or those in {@code ?status=}</td></tr>
 *   <tr><td>{@code GET /api/v1/admin/orders/{number}}</td><td>any order</td></tr>
 *   <tr><td>{@code PUT /api/v1/admin/orders/{number}/status}</td><td>move it to another status</td></tr>
 * </table>
 */
public final class OrderAdminResource implements Routes.Resource {

    private final OrderAdminFacade orders;

    /**
     * Creates the resource.
     *
     * @param orders the order management use cases
     */
    public OrderAdminResource(OrderAdminFacade orders) {
        this.orders = Objects.requireNonNull(orders, "orders");
    }

    @Override
    public void register(Routes routes) {
        routes.get("/api/v1/admin/orders", this::list)
                .get("/api/v1/admin/orders/{number}", request -> {
                    request.administrator();
                    return WebResponse.ok(orders.order(request.pathParameter("number")));
                })
                .put("/api/v1/admin/orders/{number}/status", request -> {
                    request.administrator();
                    StatusChangeRequest change = request.body(StatusChangeRequest.class);
                    return WebResponse.ok(orders.changeStatus(request.pathParameter("number"), change.status()));
                });
    }

    private WebResponse list(WebRequest request) {
        request.administrator();
        PageRequest page = PageResponse.pageOf(request);
        String status = request.query("status");
        return WebResponse.ok(PageResponse.of(status == null ? orders.orders(page) : orders.ordersIn(status, page)));
    }
}
