package com.bookstore.sales.rest;

import com.bookstore.sales.api.CartChangedException;
import com.bookstore.sales.api.CheckoutFacade;
import com.bookstore.sales.api.OrderDetails;
import com.bookstore.sales.api.OrderFacade;
import com.bookstore.sales.api.PlaceOrderCommand;
import com.bookstore.sales.rest.SalesRequests.PlaceOrderRequest;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.web.PageResponse;
import com.bookstore.shared.web.Routes;
import com.bookstore.shared.web.WebException;
import com.bookstore.shared.web.WebRequest;
import com.bookstore.shared.web.WebResponse;

import java.util.Map;
import java.util.Objects;

/**
 * Placing orders and the customer's order history (ORD-1 – ORD-5, ORD-8, ORD-9). The customer
 * comes from the sign-in, so nobody can order for or read the orders of someone else.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code POST /api/v1/orders}</td><td>order everything in the cart</td></tr>
 *   <tr><td>{@code GET /api/v1/orders}</td><td>the customer's orders, newest first</td></tr>
 *   <tr><td>{@code GET /api/v1/orders/{number}}</td><td>one of them</td></tr>
 * </table>
 *
 * <p>If the cart had to be corrected at checkout, the answer is a 409 problem with the code
 * {@code cart.changed} and the corrections as its {@code adjustments} member; nothing was ordered.
 */
public final class OrderResource implements Routes.Resource {

    private final CheckoutFacade checkout;
    private final OrderFacade orders;

    /**
     * Creates the resource.
     *
     * @param checkout placing orders
     * @param orders   the customer's orders
     */
    public OrderResource(CheckoutFacade checkout, OrderFacade orders) {
        this.checkout = Objects.requireNonNull(checkout, "checkout");
        this.orders = Objects.requireNonNull(orders, "orders");
    }

    @Override
    public void register(Routes routes) {
        routes.post("/api/v1/orders", this::place)
                .get("/api/v1/orders", request -> WebResponse.ok(
                        PageResponse.of(orders.myOrders(me(request), PageResponse.pageOf(request)))))
                .get("/api/v1/orders/{number}", request -> WebResponse.ok(
                        orders.myOrder(me(request), request.pathParameter("number"))));
    }

    private WebResponse place(WebRequest request) {
        PlaceOrderRequest form = request.body(PlaceOrderRequest.class);
        try {
            OrderDetails order = checkout.placeOrder(
                    new PlaceOrderCommand(me(request), form.addressId(), form.paymentMethod()));
            return WebResponse.created("/api/v1/orders/" + order.orderNumber(), order);
        } catch (CartChangedException changed) {
            throw WebException.conflict(changed.code(), changed.getMessage(),
                    Map.of("adjustments", CartResponse.notices(changed.adjustments())));
        }
    }

    private static CustomerId me(WebRequest request) {
        return request.customer().customerId();
    }
}
