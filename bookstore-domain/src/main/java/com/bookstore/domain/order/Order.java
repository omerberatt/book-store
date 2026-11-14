package com.bookstore.domain.order;

import com.bookstore.common.error.AccessDeniedException;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.money.Money;
import com.bookstore.common.validation.Guard;
import com.bookstore.domain.Entity;
import com.bookstore.domain.cart.Cart;
import com.bookstore.domain.cart.CartItem;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.InsufficientStockException;
import com.bookstore.domain.customer.Address;
import com.bookstore.domain.event.OrderPlaced;
import com.bookstore.domain.event.OrderStatusChanged;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A confirmed purchase.
 *
 * <p>An order is created from a cart with {@link #place}, which copies titles and prices, takes the
 * copies out of stock and works out the totals. After that only its {@link OrderStatus status}
 * changes, following the allowed transitions.
 */
public class Order extends Entity {

    /** Error code for checking out an empty cart. */
    public static final String EMPTY_CART = "cart.empty";

    private final OrderNumber number;
    private final long customerId;
    private final List<OrderLine> lines;
    private final Address shippingAddress;
    private final PaymentMethod paymentMethod;
    private final Money subtotal;
    private final Money shippingCost;
    private OrderStatus status;
    private final Instant placedAt;
    private Instant statusChangedAt;

    private Order(OrderNumber number, long customerId, List<OrderLine> lines, Address shippingAddress,
                  PaymentMethod paymentMethod, Money shippingCost, OrderStatus status, Instant placedAt,
                  Instant statusChangedAt) {
        this.number = Guard.requireNonNull(number, "orderNumber");
        Guard.require(customerId > 0, "customerId", "must be a stored customer");
        this.customerId = customerId;
        this.lines = List.copyOf(Guard.requireNotEmpty(lines, "lines"));
        this.shippingAddress = Guard.requireNonNull(shippingAddress, "shippingAddress");
        this.paymentMethod = Guard.requireNonNull(paymentMethod, "paymentMethod");
        this.shippingCost = Guard.requireNonNull(shippingCost, "shippingCost");
        Guard.require(!shippingCost.isNegative(), "shippingCost", "must not be negative");
        this.status = Guard.requireNonNull(status, "status");
        this.placedAt = Guard.requireNonNull(placedAt, "placedAt");
        this.statusChangedAt = Guard.requireNonNull(statusChangedAt, "statusChangedAt");
        this.subtotal = Money.sum(this.lines.stream().map(OrderLine::lineTotal));
    }

    /**
     * Places an order for everything in a customer's cart.
     *
     * <p>All lines are checked before any stock is taken, so a failure leaves every book unchanged.
     * The caller stores the order and the books in one transaction and then empties the cart.
     *
     * @param number          the new order's number
     * @param customerId      the customer placing the order
     * @param cart            the customer's cart, not empty
     * @param books           every book in the cart, keyed by id
     * @param shippingAddress where to deliver
     * @param paymentMethod   how the customer pays
     * @param shippingPolicy  the rule for the shipping cost
     * @param clock           the clock giving the order time
     * @return the order, not yet stored, with an {@link OrderPlaced} event raised
     * @throws ConflictException          if the cart is empty or a book is no longer sold
     * @throws InsufficientStockException if a book does not have enough stock
     * @throws AccessDeniedException      if the cart belongs to someone else
     */
    public static Order place(OrderNumber number, long customerId, Cart cart, Map<Long, Book> books,
                              Address shippingAddress, PaymentMethod paymentMethod,
                              ShippingPolicy shippingPolicy, Clock clock) {
        Guard.requireNonNull(cart, "cart");
        if (cart.isEmpty()) {
            throw new ConflictException(EMPTY_CART, "Cannot place an order with an empty cart");
        }
        if (cart.customerId().map(owner -> owner != customerId).orElse(true)) {
            throw new AccessDeniedException("The cart does not belong to customer " + customerId);
        }

        List<OrderLine> lines = new ArrayList<>();
        for (CartItem item : cart) {
            Book book = books.get(item.bookId());
            if (book == null) {
                throw new NotFoundException("Book", item.bookId());
            }
            requireSellable(book, item.quantity());
            lines.add(OrderLine.of(book, item.quantity()));
        }
        for (CartItem item : cart) {
            books.get(item.bookId()).reserve(item.quantity());
        }

        Money subtotal = Money.sum(lines.stream().map(OrderLine::lineTotal));
        Money shippingCost = Guard.requireNonNull(shippingPolicy, "shippingPolicy").costFor(subtotal);
        Instant now = Instant.now(clock);
        Order order = new Order(number, customerId, lines, shippingAddress, paymentMethod, shippingCost,
                OrderStatus.CREATED, now, now);
        order.raise(new OrderPlaced(number, customerId, order.total(), now));
        return order;
    }

    /**
     * Restores an order loaded from storage.
     *
     * @param id              the stored id
     * @param version         the stored version
     * @param number          the order number
     * @param customerId      the owner
     * @param lines           the stored lines
     * @param shippingAddress the delivery address
     * @param paymentMethod   the payment method
     * @param shippingCost    the shipping cost charged
     * @param status          the current status
     * @param placedAt        when the order was placed
     * @param statusChangedAt when the status last changed
     * @return the order
     */
    public static Order restore(long id, int version, OrderNumber number, long customerId, List<OrderLine> lines,
                                Address shippingAddress, PaymentMethod paymentMethod, Money shippingCost,
                                OrderStatus status, Instant placedAt, Instant statusChangedAt) {
        Order order = new Order(number, customerId, lines, shippingAddress, paymentMethod, shippingCost, status,
                placedAt, statusChangedAt);
        order.markPersisted(id, version);
        return order;
    }

    // ---------------------------------------------------------------- queries

    /** @return the order number */
    public OrderNumber number() {
        return number;
    }

    /** @return the id of the customer who placed the order */
    public long customerId() {
        return customerId;
    }

    /** @return the lines, in cart order */
    public List<OrderLine> lines() {
        return lines;
    }

    /** @return the delivery address as it was when the order was placed */
    public Address shippingAddress() {
        return shippingAddress;
    }

    /** @return the payment method */
    public PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    /** @return the price of the books */
    public Money subtotal() {
        return subtotal;
    }

    /** @return the shipping cost */
    public Money shippingCost() {
        return shippingCost;
    }

    /** @return the amount to pay: subtotal plus shipping */
    public Money total() {
        return subtotal.plus(shippingCost);
    }

    /** @return the current status */
    public OrderStatus status() {
        return status;
    }

    /** @return when the order was placed */
    public Instant placedAt() {
        return placedAt;
    }

    /** @return when the status last changed */
    public Instant statusChangedAt() {
        return statusChangedAt;
    }

    /**
     * Tells whether the order belongs to a customer.
     *
     * @param someCustomerId the customer's id
     * @return {@code true} if that customer placed the order
     */
    public boolean isOwnedBy(long someCustomerId) {
        return customerId == someCustomerId;
    }

    /**
     * Tells whether the order includes a book.
     *
     * @param bookId the book's id
     * @return {@code true} if one of the lines is for that book
     */
    public boolean contains(long bookId) {
        return lines.stream().anyMatch(line -> line.bookId() == bookId);
    }

    /** @return the number of copies across all lines */
    public int totalQuantity() {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }

    // ---------------------------------------------------------------- commands

    /**
     * Records that the payment went through.
     *
     * @param clock the clock giving the change time
     */
    public void markPaid(Clock clock) {
        moveTo(OrderStatus.PAID, clock);
    }

    /**
     * Records that the parcel was handed to the courier.
     *
     * @param clock the clock giving the change time
     */
    public void ship(Clock clock) {
        moveTo(OrderStatus.SHIPPED, clock);
    }

    /**
     * Records that the customer received the parcel.
     *
     * @param clock the clock giving the change time
     */
    public void deliver(Clock clock) {
        moveTo(OrderStatus.DELIVERED, clock);
    }

    /**
     * Cancels the order and puts every copy back into stock.
     *
     * @param books every book in the order, keyed by id
     * @param clock the clock giving the change time
     * @throws ConflictException if the order has already been shipped, delivered or cancelled
     */
    public void cancel(Map<Long, Book> books, Clock clock) {
        status.requireCanMoveTo(OrderStatus.CANCELLED);
        for (OrderLine line : lines) {
            if (!books.containsKey(line.bookId())) {
                throw new NotFoundException("Book", line.bookId());
            }
        }
        moveTo(OrderStatus.CANCELLED, clock);
        for (OrderLine line : lines) {
            books.get(line.bookId()).release(line.quantity());
        }
    }

    @Override
    public String toString() {
        return "Order[" + number + ", " + status + ", " + total() + "]";
    }

    private void moveTo(OrderStatus target, Clock clock) {
        status.requireCanMoveTo(target);
        OrderStatus previous = status;
        Instant now = Instant.now(clock);
        status = target;
        statusChangedAt = now;
        raise(new OrderStatusChanged(number, customerId, previous, target, now));
    }

    private static void requireSellable(Book book, int quantity) {
        if (!book.isActive()) {
            throw new ConflictException("book.inactive", "'" + book.title() + "' is no longer sold");
        }
        if (!book.stock().hasAtLeast(quantity)) {
            throw new InsufficientStockException(book.title(), quantity, book.stock().quantity());
        }
    }
}
