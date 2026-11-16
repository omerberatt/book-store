package com.bookstore.sales.application;

import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.kernel.address.Address;
import com.bookstore.sales.domain.Order;
import com.bookstore.sales.domain.OrderLine;
import com.bookstore.sales.domain.OrderStatus;
import com.bookstore.sales.domain.PaymentMethod;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * An order's detail page.
 *
 * @param orderNumber     the order number
 * @param customerId      the customer who placed it
 * @param placedAt        when it was placed
 * @param statusChangedAt when the status last changed
 * @param status          the current status
 * @param nextStatuses    the statuses an administrator may move it to
 * @param lines           the lines, with the titles and prices at the time of the order
 * @param shippingAddress the delivery address
 * @param paymentMethod   how it is paid
 * @param subtotal        the price of the books
 * @param shippingCost    the shipping cost
 * @param total           the amount to pay
 */
public record OrderDetails(String orderNumber, long customerId, Instant placedAt, Instant statusChangedAt,
                           OrderStatus status, Set<OrderStatus> nextStatuses, List<OrderLine> lines,
                           Address shippingAddress, PaymentMethod paymentMethod, Money subtotal, Money shippingCost,
                           Money total) {

    /** Copies the collections. */
    public OrderDetails {
        nextStatuses = Set.copyOf(nextStatuses);
        lines = List.copyOf(lines);
    }

    /**
     * Copies an order.
     *
     * @param order a stored order
     * @return the details
     */
    public static OrderDetails of(Order order) {
        return new OrderDetails(order.number().value(), order.customerId(), order.placedAt(),
                order.statusChangedAt(), order.status(), order.status().allowedNext(), order.lines(),
                order.shippingAddress(), order.paymentMethod(), order.subtotal(), order.shippingCost(),
                order.total());
    }
}
