package com.bookstore.application.cart;

import com.bookstore.common.error.ConflictException;
import com.bookstore.domain.cart.CartAdjustment;

import java.util.List;

/**
 * Signals that the cart had to be adjusted at checkout because prices or stock changed, so the
 * customer must look at it again before ordering. Maps to HTTP 409.
 */
public class CartChangedException extends ConflictException {

    /** Error code for a cart that changed at checkout. */
    public static final String CODE = "cart.changed";

    private final List<CartAdjustment> adjustments;

    /**
     * Creates the exception.
     *
     * @param adjustments what changed, at least one
     */
    public CartChangedException(List<CartAdjustment> adjustments) {
        super(CODE, "The cart changed: " + adjustments);
        this.adjustments = List.copyOf(adjustments);
    }

    /**
     * Returns what changed.
     *
     * @return the adjustments
     */
    public List<CartAdjustment> adjustments() {
        return adjustments;
    }
}
