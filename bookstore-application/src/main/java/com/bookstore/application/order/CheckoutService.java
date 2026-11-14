package com.bookstore.application.order;

import com.bookstore.application.cart.CartChangedException;
import com.bookstore.application.port.Notifier;
import com.bookstore.application.port.OrderNumberSequence;
import com.bookstore.application.port.PaymentGateway;
import com.bookstore.application.port.PaymentGateway.PaymentResult;
import com.bookstore.application.port.TransactionRunner;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.domain.cart.Cart;
import com.bookstore.domain.cart.CartAdjustment;
import com.bookstore.domain.cart.CartRepository;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.BookRepository;
import com.bookstore.domain.customer.Address;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.customer.CustomerRepository;
import com.bookstore.domain.event.DomainEvent;
import com.bookstore.domain.order.Order;
import com.bookstore.domain.order.OrderRepository;
import com.bookstore.domain.order.PaymentMethod;
import com.bookstore.domain.order.ShippingPolicy;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a customer's cart into an order (ORD-1 to ORD-5, ORD-9 to ORD-11).
 *
 * <p>Checkout runs in two transactions:
 *
 * <ol>
 *   <li>Check the cart against the current catalog. If anything changed (a price, the stock, a
 *       book no longer sold), store the corrected cart and stop with a {@link CartChangedException},
 *       so the customer never pays an amount they have not seen.</li>
 *   <li>Place the order: take the copies out of stock, charge the card if paying by card, store
 *       the order and the books, and empty the cart. If anything fails, nothing is stored.</li>
 * </ol>
 *
 * <p>Notifications are sent only after the second transaction has committed.
 */
public class CheckoutService {

    private final CustomerRepository customers;
    private final CartRepository carts;
    private final BookRepository books;
    private final OrderRepository orders;
    private final OrderNumberSequence orderNumbers;
    private final PaymentGateway paymentGateway;
    private final ShippingPolicy shippingPolicy;
    private final TransactionRunner transactions;
    private final Notifier notifier;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param customers      where customers are stored
     * @param carts          where carts are stored
     * @param books          where books are stored
     * @param orders         where orders are stored
     * @param orderNumbers   hands out order numbers
     * @param paymentGateway charges cards
     * @param shippingPolicy the rule for the shipping cost
     * @param transactions   runs work in a transaction
     * @param notifier       tells customers about their orders
     * @param clock          the clock giving order times
     */
    public CheckoutService(CustomerRepository customers, CartRepository carts, BookRepository books,
                           OrderRepository orders, OrderNumberSequence orderNumbers, PaymentGateway paymentGateway,
                           ShippingPolicy shippingPolicy, TransactionRunner transactions, Notifier notifier,
                           Clock clock) {
        this.customers = customers;
        this.carts = carts;
        this.books = books;
        this.orders = orders;
        this.orderNumbers = orderNumbers;
        this.paymentGateway = paymentGateway;
        this.shippingPolicy = shippingPolicy;
        this.transactions = transactions;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Places an order for everything in the customer's cart.
     *
     * <p>Orders paid by card are charged at once and become {@code PAID}. Bank transfers and cash
     * on delivery stay {@code CREATED} until an administrator records the payment.
     *
     * @param command the checkout form
     * @return the new order
     * @throws CartChangedException     if the cart had to be corrected; nothing was ordered
     * @throws ConflictException        if the cart is empty
     * @throws PaymentDeclinedException if the card was refused; nothing was ordered
     * @throws NotFoundException        if the customer or the address does not exist
     */
    public OrderDetails placeOrder(PlaceOrderCommand command) {
        List<CartAdjustment> adjustments = transactions.inTransaction(() -> reconcileCart(command.customerId()));
        if (!adjustments.isEmpty()) {
            throw new CartChangedException(adjustments);
        }

        Order order = transactions.inTransaction(() -> place(command));

        List<DomainEvent> events = order.pullEvents();
        events.forEach(notifier::publish);
        return OrderDetails.of(order);
    }

    private List<CartAdjustment> reconcileCart(long customerId) {
        Cart cart = carts.findByCustomerId(customerId).orElseGet(() -> Cart.forCustomer(customerId));
        List<CartAdjustment> adjustments = cart.reconcile(booksIn(cart));
        if (!adjustments.isEmpty()) {
            carts.save(cart);
        }
        return adjustments;
    }

    private Order place(PlaceOrderCommand command) {
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new NotFoundException("Customer", command.customerId()));
        Address address = customer.address(command.addressId()).address();
        Cart cart = carts.findByCustomerId(customer.id()).orElseGet(() -> Cart.forCustomer(customer.id()));
        Map<Long, Book> cartBooks = booksIn(cart);

        // Something may have changed since the first transaction; never charge a price the customer has not seen.
        List<CartAdjustment> lateChanges = cart.reconcile(cartBooks);
        if (!lateChanges.isEmpty()) {
            throw new CartChangedException(lateChanges);
        }

        Order order = Order.place(orderNumbers.next(Year.now(clock)), customer.id(), cart, cartBooks, address,
                command.paymentMethod(), shippingPolicy, clock);

        if (command.paymentMethod() == PaymentMethod.CREDIT_CARD) {
            PaymentResult result = paymentGateway.charge(order.number(), order.total());
            switch (result) {
                case PaymentResult.Approved approved -> order.markPaid(clock);
                case PaymentResult.Declined declined -> throw new PaymentDeclinedException(declined.reason());
            }
        }

        cartBooks.values().forEach(books::save);
        orders.save(order);
        cart.clear();
        carts.save(cart);
        return order;
    }

    private Map<Long, Book> booksIn(Cart cart) {
        return books.findAllById(cart.bookIds()).stream()
                .collect(Collectors.toMap(Book::id, Function.identity()));
    }
}
