package com.bookstore.domain.cart;

import com.bookstore.common.error.ConflictException;
import com.bookstore.common.money.Money;
import com.bookstore.common.validation.Guard;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.InsufficientStockException;

import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Books a visitor or customer intends to buy.
 *
 * <p>A guest cart lives in the visitor's HTTP session, which is why the class is
 * {@link Serializable}. A customer cart is stored in the database under the customer's id. When a
 * visitor signs in, the guest cart is {@linkplain #mergeFrom(Cart) merged} into the customer cart.
 *
 * <p>Lines keep the order in which books were first added.
 */
public final class Cart implements Iterable<CartItem>, Serializable {

    /** Most copies of one book a cart can hold. */
    public static final int MAX_QUANTITY_PER_LINE = 10;

    /** Most different books a cart can hold. */
    public static final int MAX_LINES = 50;

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long customerId;
    private final LinkedHashMap<Long, CartItem> items = new LinkedHashMap<>();

    private Cart(Long customerId) {
        this.customerId = customerId;
    }

    /**
     * Creates an empty cart for a visitor who has not signed in.
     *
     * @return the cart
     */
    public static Cart guest() {
        return new Cart(null);
    }

    /**
     * Creates an empty cart for a customer.
     *
     * @param customerId the customer's id
     * @return the cart
     */
    public static Cart forCustomer(long customerId) {
        Guard.require(customerId > 0, "customerId", "must be a stored customer");
        return new Cart(customerId);
    }

    /**
     * Restores a customer's cart loaded from storage.
     *
     * @param customerId the customer's id
     * @param items      the stored lines, in order
     * @return the cart
     */
    public static Cart restore(long customerId, List<CartItem> items) {
        Cart cart = forCustomer(customerId);
        for (CartItem item : items) {
            cart.items.put(item.bookId(), item);
        }
        return cart;
    }

    // ---------------------------------------------------------------- queries

    /**
     * Returns the owner of the cart.
     *
     * @return the customer's id, or empty for a guest cart
     */
    public Optional<Long> customerId() {
        return Optional.ofNullable(customerId);
    }

    /** @return the lines, in the order books were first added */
    public List<CartItem> items() {
        return List.copyOf(items.values());
    }

    /** @return the ids of the books in the cart */
    public Set<Long> bookIds() {
        return Set.copyOf(items.keySet());
    }

    /**
     * Returns how many copies of a book are in the cart.
     *
     * @param bookId the book's id
     * @return the quantity, zero if the book is not in the cart
     */
    public int quantityOf(long bookId) {
        CartItem item = items.get(bookId);
        return item == null ? 0 : item.quantity();
    }

    /** @return the number of copies across all lines, shown on the cart icon */
    public int totalQuantity() {
        return items.values().stream().mapToInt(CartItem::quantity).sum();
    }

    /** @return {@code true} if the cart has no lines */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Adds up the cart using prices supplied by the caller, because the cart itself does not store
     * prices.
     *
     * @param unitPrice returns the current price of a book given its id
     * @return the sum of price times quantity over all lines
     */
    public Money subtotal(Function<Long, Money> unitPrice) {
        return Money.sum(items.values().stream()
                .map(item -> unitPrice.apply(item.bookId()).times(item.quantity())));
    }

    /**
     * Iterates over the lines in order. The iterator does not support removal; use
     * {@link #remove(long)} instead.
     *
     * @return an iterator over the lines
     */
    @Override
    public Iterator<CartItem> iterator() {
        return new LineIterator();
    }

    // ---------------------------------------------------------------- commands

    /**
     * Adds copies of a book, on top of any already in the cart.
     *
     * @param book     a stored, active book
     * @param quantity copies to add, positive
     * @throws ConflictException           if the book is not for sale
     * @throws InsufficientStockException  if the stock does not cover the new quantity
     */
    public void add(Book book, int quantity) {
        Guard.requirePositive(quantity, "quantity");
        put(book, quantityOf(book.id()) + quantity);
    }

    /**
     * Sets the number of copies of a book. Zero removes the line.
     *
     * @param book     a stored book
     * @param quantity the new quantity, zero or greater
     */
    public void changeQuantity(Book book, int quantity) {
        Guard.requireNonNegative(quantity, "quantity");
        if (quantity == 0) {
            remove(book.id());
        } else {
            put(book, quantity);
        }
    }

    /**
     * Removes a book from the cart. Removing a book that is not in the cart does nothing.
     *
     * @param bookId the book's id
     */
    public void remove(long bookId) {
        items.remove(bookId);
    }

    /** Removes every line. */
    public void clear() {
        items.clear();
    }

    /**
     * Adds the lines of another cart to this one, typically a guest cart after signing in.
     * Quantities of the same book are added up and capped at {@link #MAX_QUANTITY_PER_LINE}; stock is
     * checked again when the cart is shown.
     *
     * @param other the cart to merge in; it is not changed
     */
    public void mergeFrom(Cart other) {
        for (CartItem item : other) {
            int merged = Math.min(MAX_QUANTITY_PER_LINE, quantityOf(item.bookId()) + item.quantity());
            if (items.containsKey(item.bookId()) || items.size() < MAX_LINES) {
                items.put(item.bookId(), new CartItem(item.bookId(), merged));
            }
        }
    }

    @Override
    public String toString() {
        return "Cart[" + (customerId == null ? "guest" : "customer=" + customerId) + ", " + items.values() + "]";
    }

    private void put(Book book, int newQuantity) {
        Guard.requireNonNull(book, "book");
        if (!book.isActive()) {
            throw new ConflictException("book.inactive", "'" + book.title() + "' is no longer sold");
        }
        Guard.require(newQuantity <= MAX_QUANTITY_PER_LINE, "quantity",
                "at most " + MAX_QUANTITY_PER_LINE + " copies of one book can be ordered");
        if (!book.stock().hasAtLeast(newQuantity)) {
            throw new InsufficientStockException(book.title(), newQuantity, book.stock().quantity());
        }
        Guard.require(items.containsKey(book.id()) || items.size() < MAX_LINES, "items",
                "a cart can hold at most " + MAX_LINES + " different books");
        items.put(book.id(), new CartItem(book.id(), newQuantity));
    }

    /**
     * An inner (non-static) class: each iterator belongs to one cart and reads that cart's lines
     * directly.
     */
    private final class LineIterator implements Iterator<CartItem> {

        private final Iterator<CartItem> delegate = items.values().iterator();

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public CartItem next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return delegate.next();
        }
    }
}
