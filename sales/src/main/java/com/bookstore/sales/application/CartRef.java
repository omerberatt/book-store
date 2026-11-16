package com.bookstore.sales.application;

import com.bookstore.sales.domain.Cart;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.kernel.validation.Guard;

/**
 * Says which cart a cart operation works on: a visitor's cart from the HTTP session, or a signed-in
 * customer's stored cart.
 */
public sealed interface CartRef {

    /**
     * Refers to a visitor's cart, which the web layer keeps in the session. Operations change the
     * given object in place.
     *
     * @param cart the session cart
     * @return the reference
     */
    static CartRef guest(Cart cart) {
        return new Guest(cart);
    }

    /**
     * Refers to a signed-in customer's stored cart. A customer without a cart gets an empty one.
     *
     * @param customerId the customer's id
     * @return the reference
     */
    static CartRef customer(CustomerId customerId) {
        return new Customer(customerId);
    }

    /**
     * A visitor's session cart.
     *
     * @param cart the cart, which must be a guest cart
     */
    record Guest(Cart cart) implements CartRef {

        /** Requires a guest cart. */
        public Guest {
            Guard.requireNonNull(cart, "cart");
            Guard.require(cart.customerId().isEmpty(), "cart", "must be a guest cart");
        }
    }

    /**
     * A customer's stored cart.
     *
     * @param customerId the customer's id
     */
    record Customer(CustomerId customerId) implements CartRef {

        /** Requires a stored customer. */
        public Customer {
            Guard.requireNonNull(customerId, "customerId");
        }
    }
}
