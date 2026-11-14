package com.bookstore.domain.cart;

import com.bookstore.common.validation.Guard;

import java.io.Serializable;

/**
 * One line of a cart: a book and how many copies of it. Prices are not stored here, because a cart
 * always shows the book's current price.
 *
 * @param bookId   the book's id
 * @param quantity copies, from 1 to {@link Cart#MAX_QUANTITY_PER_LINE}
 */
public record CartItem(long bookId, int quantity) implements Serializable {

    /** Validates both parts. */
    public CartItem {
        Guard.require(bookId > 0, "bookId", "must be a stored book");
        Guard.requireBetween(quantity, 1, Cart.MAX_QUANTITY_PER_LINE, "quantity");
    }
}
