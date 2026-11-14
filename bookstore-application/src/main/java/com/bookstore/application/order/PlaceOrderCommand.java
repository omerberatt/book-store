package com.bookstore.application.order;

import com.bookstore.common.validation.Guard;
import com.bookstore.domain.order.PaymentMethod;

/**
 * The checkout form (ORD-2).
 *
 * @param customerId    the signed-in customer
 * @param addressId     the chosen saved address
 * @param paymentMethod the chosen payment method
 */
public record PlaceOrderCommand(long customerId, long addressId, PaymentMethod paymentMethod) {

    /** Rejects a missing payment method. */
    public PlaceOrderCommand {
        Guard.requireNonNull(paymentMethod, "paymentMethod");
    }
}
