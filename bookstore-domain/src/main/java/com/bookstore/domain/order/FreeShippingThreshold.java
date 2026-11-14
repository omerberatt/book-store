package com.bookstore.domain.order;

import com.bookstore.common.money.Money;
import com.bookstore.common.validation.Guard;

/**
 * Free shipping from a minimum order value, a flat fee below it.
 *
 * @param threshold subtotal from which shipping is free, inclusive
 * @param flatFee   fee charged below the threshold
 */
public record FreeShippingThreshold(Money threshold, Money flatFee) implements ShippingPolicy {

    /** Rejects missing or negative amounts. */
    public FreeShippingThreshold {
        Guard.requireNonNull(threshold, "threshold");
        Guard.requireNonNull(flatFee, "flatFee");
        Guard.require(!threshold.isNegative(), "threshold", "must not be negative");
        Guard.require(!flatFee.isNegative(), "flatFee", "must not be negative");
    }

    @Override
    public Money costFor(Money subtotal) {
        return subtotal.isAtLeast(threshold) ? Money.ZERO : flatFee;
    }
}
