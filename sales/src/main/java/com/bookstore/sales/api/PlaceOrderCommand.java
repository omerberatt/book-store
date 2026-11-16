package com.bookstore.sales.api;

import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.kernel.validation.Guard;

/**
 * The checkout form (ORD-2).
 *
 * @param customerId    the signed-in customer
 * @param addressId     the chosen saved address
 * @param paymentMethod the chosen payment method: {@code CREDIT_CARD} or {@code BANK_TRANSFER}
 */
public record PlaceOrderCommand(CustomerId customerId, long addressId, String paymentMethod) {

    /** Rejects a missing payment method. */
    public PlaceOrderCommand {
        Guard.requireNonNull(paymentMethod, "paymentMethod");
    }
}
