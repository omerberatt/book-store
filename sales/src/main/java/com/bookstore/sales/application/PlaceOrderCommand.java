package com.bookstore.sales.application;

import com.bookstore.sales.domain.PaymentMethod;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.kernel.validation.Guard;

/**
 * The checkout form (ORD-2).
 *
 * @param customerId    the signed-in customer
 * @param addressId     the chosen saved address
 * @param paymentMethod the chosen payment method
 */
public record PlaceOrderCommand(CustomerId customerId, long addressId, PaymentMethod paymentMethod) {

    /** Rejects a missing payment method. */
    public PlaceOrderCommand {
        Guard.requireNonNull(paymentMethod, "paymentMethod");
    }
}
