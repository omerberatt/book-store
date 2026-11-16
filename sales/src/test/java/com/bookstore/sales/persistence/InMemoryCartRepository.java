package com.bookstore.sales.persistence;

import com.bookstore.sales.domain.Cart;
import com.bookstore.sales.domain.CartRepository;
import com.bookstore.shared.kernel.identity.CustomerId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Customers' carts keyed by customer id. */
public class InMemoryCartRepository implements CartRepository {

    private final Map<CustomerId, Cart> byCustomerId = new HashMap<>();

    @Override
    public Optional<Cart> findByCustomerId(CustomerId customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }

    @Override
    public Cart save(Cart cart) {
        CustomerId owner = cart.customerId().orElseThrow(() -> new IllegalArgumentException("guest carts are not stored"));
        byCustomerId.put(owner, cart);
        return cart;
    }
}
