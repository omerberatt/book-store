package com.bookstore.application.cart;

import com.bookstore.application.port.TransactionRunner;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.money.Money;
import com.bookstore.domain.cart.Cart;
import com.bookstore.domain.cart.CartAdjustment;
import com.bookstore.domain.cart.CartRepository;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.BookRepository;
import com.bookstore.domain.order.ShippingPolicy;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cart use cases for visitors and customers (CRT-1 to CRT-5).
 *
 * <p>Every operation returns the cart as it should be shown: checked against the current catalog,
 * priced with current prices and with any adjustments listed.
 */
public class CartService {

    private final BookRepository books;
    private final CartRepository carts;
    private final ShippingPolicy shippingPolicy;
    private final TransactionRunner transactions;

    /**
     * Creates the service.
     *
     * @param books          where books are stored
     * @param carts          where customers' carts are stored
     * @param shippingPolicy the rule for the shipping cost
     * @param transactions   runs work in a transaction
     */
    public CartService(BookRepository books, CartRepository carts, ShippingPolicy shippingPolicy,
                       TransactionRunner transactions) {
        this.books = books;
        this.carts = carts;
        this.shippingPolicy = shippingPolicy;
        this.transactions = transactions;
    }

    /**
     * Shows a cart.
     *
     * @param ref which cart
     * @return the cart, checked against the current catalog
     */
    public CartView view(CartRef ref) {
        return change(ref, cart -> { });
    }

    /**
     * Adds copies of a book.
     *
     * @param ref      which cart
     * @param bookId   the book's id
     * @param quantity copies to add
     * @return the updated cart
     * @throws NotFoundException if the book does not exist or is deactivated
     */
    public CartView add(CartRef ref, long bookId, int quantity) {
        return change(ref, cart -> cart.add(requireActiveBook(bookId), quantity));
    }

    /**
     * Sets the number of copies of a book; zero removes it.
     *
     * @param ref      which cart
     * @param bookId   the book's id
     * @param quantity the new quantity
     * @return the updated cart
     */
    public CartView changeQuantity(CartRef ref, long bookId, int quantity) {
        return change(ref, cart -> {
            if (quantity == 0) {
                cart.remove(bookId);
            } else {
                cart.changeQuantity(requireActiveBook(bookId), quantity);
            }
        });
    }

    /**
     * Removes a book.
     *
     * @param ref    which cart
     * @param bookId the book's id
     * @return the updated cart
     */
    public CartView remove(CartRef ref, long bookId) {
        return change(ref, cart -> cart.remove(bookId));
    }

    /**
     * Empties a cart.
     *
     * @param ref which cart
     * @return the empty cart
     */
    public CartView clear(CartRef ref) {
        return change(ref, Cart::clear);
    }

    /**
     * Merges a visitor's session cart into their stored cart after they sign in (CRT-2). The web
     * layer drops the session cart afterwards.
     *
     * @param customerId the customer who just signed in
     * @param guestCart  the session cart; it is not changed
     * @return the merged cart
     */
    public CartView mergeGuestCart(long customerId, Cart guestCart) {
        return change(CartRef.customer(customerId), cart -> cart.mergeFrom(guestCart));
    }

    /**
     * Returns the number of copies in a cart, for the badge on the cart icon. Does not check the
     * catalog, so it is cheap.
     *
     * @param ref which cart
     * @return the number of copies
     */
    public int itemCount(CartRef ref) {
        return load(ref).totalQuantity();
    }

    private CartView change(CartRef ref, Consumer<Cart> operation) {
        return transactions.inTransaction(() -> {
            Cart cart = load(ref);
            operation.accept(cart);
            Map<Long, Book> current = booksIn(cart);
            List<CartAdjustment> adjustments = cart.reconcile(current);
            store(ref, cart);
            return toView(cart, current, adjustments);
        });
    }

    private Cart load(CartRef ref) {
        return switch (ref) {
            case CartRef.Guest guest -> guest.cart();
            case CartRef.Customer customer -> carts.findByCustomerId(customer.customerId())
                    .orElseGet(() -> Cart.forCustomer(customer.customerId()));
        };
    }

    private void store(CartRef ref, Cart cart) {
        switch (ref) {
            case CartRef.Guest guest -> {
                // The session cart was changed in place; the web layer keeps it in the session.
            }
            case CartRef.Customer customer -> carts.save(cart);
        }
    }

    private Map<Long, Book> booksIn(Cart cart) {
        return books.findAllById(cart.bookIds()).stream()
                .collect(Collectors.toMap(Book::id, Function.identity()));
    }

    private Book requireActiveBook(long bookId) {
        return books.findById(bookId)
                .filter(Book::isActive)
                .orElseThrow(() -> new NotFoundException("Book", bookId));
    }

    private CartView toView(Cart cart, Map<Long, Book> current, List<CartAdjustment> adjustments) {
        List<CartView.Line> lines = cart.items().stream()
                .map(item -> {
                    Book book = current.get(item.bookId());
                    return new CartView.Line(book.id(), book.title(), book.authorNames(), book.price(),
                            item.quantity(), book.price().times(item.quantity()), book.stock().quantity());
                })
                .toList();
        Money subtotal = Money.sum(lines.stream().map(CartView.Line::lineTotal));
        Money shipping = lines.isEmpty() ? Money.ZERO : shippingPolicy.costFor(subtotal);
        return new CartView(lines, subtotal, shipping, subtotal.plus(shipping), cart.totalQuantity(), adjustments);
    }
}
