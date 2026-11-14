package com.bookstore.application.order;

import com.bookstore.common.money.Money;
import com.bookstore.domain.order.Order;
import com.bookstore.domain.order.OrderStatus;
import com.bookstore.domain.order.PaymentMethod;

import java.time.Instant;

/**
 * One row of an order list.
 *
 * @param orderNumber   the order number
 * @param customerId    the customer who placed it
 * @param placedAt      when it was placed
 * @param status        the current status
 * @param paymentMethod how it is paid
 * @param total         the amount to pay
 * @param totalQuantity the number of copies
 */
public record OrderSummary(String orderNumber, long customerId, Instant placedAt, OrderStatus status,
                           PaymentMethod paymentMethod, Money total, int totalQuantity) {

    /**
     * Copies an order.
     *
     * @param order a stored order
     * @return the summary
     */
    public static OrderSummary of(Order order) {
        return new OrderSummary(order.number().value(), order.customerId(), order.placedAt(), order.status(),
                order.paymentMethod(), order.total(), order.totalQuantity());
    }
}
