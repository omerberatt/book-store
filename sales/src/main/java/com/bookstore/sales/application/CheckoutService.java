package com.bookstore.sales.application;

import com.bookstore.catalog.api.StockFacade;
import com.bookstore.customer.api.CustomerDirectory;
import com.bookstore.sales.api.CartChangedException;
import com.bookstore.sales.api.CheckoutFacade;
import com.bookstore.sales.api.OrderDetails;
import com.bookstore.sales.api.PaymentDeclinedException;
import com.bookstore.sales.api.PlaceOrderCommand;
import com.bookstore.sales.domain.Cart;
import com.bookstore.sales.domain.CartAdjustment;
import com.bookstore.sales.domain.CartRepository;
import com.bookstore.sales.domain.Order;
import com.bookstore.sales.domain.OrderNumberSequence;
import com.bookstore.sales.domain.OrderRepository;
import com.bookstore.sales.domain.PaymentGateway.PaymentResult;
import com.bookstore.sales.domain.PaymentGateway;
import com.bookstore.sales.domain.PaymentMethod;
import com.bookstore.sales.domain.ProductSnapshot;
import com.bookstore.sales.domain.ShippingPolicy;
import com.bookstore.shared.kernel.address.Address;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.error.RuleViolationException;
import com.bookstore.shared.kernel.error.ValidationException;
import com.bookstore.shared.kernel.event.Notifier;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.kernel.transaction.TransactionRunner;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Map;

/**
 * Turns a customer's cart into an order (ORD-1 to ORD-5, ORD-9 to ORD-11).
 *
 * <p>Checkout runs in two transactions (ADR-0014):
 *
 * <ol>
 *   <li>Check the cart against the current catalog. If anything changed (a price, the stock, a
 *       book no longer sold), store the corrected cart and stop with a {@link CartChangedException},
 *       so the customer never pays an amount they have not seen.</li>
 *   <li>Place the order: ask the catalog to take the copies out of stock, charge the card if paying
 *       by card, store the order and empty the cart. If anything fails, nothing is stored.</li>
 * </ol>
 *
 * <p>The catalog and customer modules are reached only through their {@code api} packages
 * ({@link StockFacade}, {@link CustomerDirectory}). Notifications are sent only after the second
 * transaction has committed.
 */
public class CheckoutService implements CheckoutFacade {

    private final CustomerDirectory customers;
    private final CartRepository carts;
    private final StockFacade stock;
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
     * @param customers      the customer module's directory, for delivery addresses
     * @param carts          where carts are stored
     * @param stock          the catalog's prices and stock
     * @param orders         where orders are stored
     * @param orderNumbers   hands out order numbers
     * @param paymentGateway charges cards
     * @param shippingPolicy the rule for the shipping cost
     * @param transactions   runs work in a transaction
     * @param notifier       tells customers about their orders
     * @param clock          the clock giving order times
     */
    public CheckoutService(CustomerDirectory customers, CartRepository carts, StockFacade stock,
                           OrderRepository orders, OrderNumberSequence orderNumbers, PaymentGateway paymentGateway,
                           ShippingPolicy shippingPolicy, TransactionRunner transactions, Notifier notifier,
                           Clock clock) {
        this.customers = customers;
        this.carts = carts;
        this.stock = stock;
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
     * on delivery stay {@code CREATED} until an administrator records the payment (ADR-0013).
     *
     * @param command the checkout form
     * @return the new order
     * @throws CartChangedException     if the cart had to be corrected; nothing was ordered
     * @throws RuleViolationException   if the cart is empty
     * @throws ValidationException      if the payment method is unknown
     * @throws PaymentDeclinedException if the card was refused; nothing was ordered
     * @throws NotFoundException        if the customer or the address does not exist
     */
    @Override
    public OrderDetails placeOrder(PlaceOrderCommand command) {
        PaymentMethod paymentMethod = SalesMapper.paymentMethodOf(command.paymentMethod());
        List<CartAdjustment> adjustments = transactions.inTransaction(() -> reconcileCart(command.customerId()));
        if (!adjustments.isEmpty()) {
            throw new CartChangedException(SalesMapper.noticesOf(adjustments));
        }

        Order order = transactions.inTransaction(() -> place(command, paymentMethod));

        order.pullEvents().forEach(notifier::publish);
        return SalesMapper.detailsOf(order);
    }

    private List<CartAdjustment> reconcileCart(CustomerId customerId) {
        Cart cart = cartOf(customerId);
        List<CartAdjustment> adjustments = cart.reconcile(Products.load(stock, cart.bookIds()));
        if (!adjustments.isEmpty()) {
            carts.save(cart);
        }
        return adjustments;
    }

    private Order place(PlaceOrderCommand command, PaymentMethod paymentMethod) {
        Address address = customers.shippingAddress(command.customerId(), command.addressId());
        Cart cart = cartOf(command.customerId());
        Map<BookId, ProductSnapshot> products = Products.load(stock, cart.bookIds());

        // Something may have changed since the first transaction; never charge a price the customer has not seen.
        List<CartAdjustment> lateChanges = cart.reconcile(products);
        if (!lateChanges.isEmpty()) {
            throw new CartChangedException(SalesMapper.noticesOf(lateChanges));
        }

        Order order = Order.place(orderNumbers.next(Year.now(clock)), command.customerId(), cart, products, address,
                paymentMethod, shippingPolicy, clock);
        stock.reserve(order.quantitiesByBook());

        if (paymentMethod == PaymentMethod.CREDIT_CARD) {
            PaymentResult result = paymentGateway.charge(order.number(), order.total());
            switch (result) {
                case PaymentResult.Approved approved -> order.markPaid(clock);
                case PaymentResult.Declined declined -> throw new PaymentDeclinedException(declined.reason());
            }
        }

        orders.save(order);
        cart.clear();
        carts.save(cart);
        return order;
    }

    private Cart cartOf(CustomerId customerId) {
        return carts.findByCustomerId(customerId).orElseGet(() -> Cart.forCustomer(customerId));
    }
}
