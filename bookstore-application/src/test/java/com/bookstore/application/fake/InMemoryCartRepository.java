package com.bookstore.application.fake;

import com.bookstore.domain.cart.Cart;
import com.bookstore.domain.cart.CartRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Customers' carts keyed by customer id. */
public class InMemoryCartRepository implements CartRepository {

    private final Map<Long, Cart> byCustomerId = new HashMap<>();

    @Override
    public Optional<Cart> findByCustomerId(long customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }

    @Override
    public Cart save(Cart cart) {
        long owner = cart.customerId().orElseThrow(() -> new IllegalArgumentException("guest carts are not stored"));
        byCustomerId.put(owner, cart);
        return cart;
    }
}
