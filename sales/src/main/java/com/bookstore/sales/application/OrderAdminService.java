package com.bookstore.sales.application;

import com.bookstore.catalog.api.StockFacade;
import com.bookstore.sales.domain.Order;
import com.bookstore.sales.domain.OrderNumber;
import com.bookstore.sales.domain.OrderRepository;
import com.bookstore.sales.domain.OrderStatus;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.event.Notifier;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.transaction.TransactionRunner;

import java.time.Clock;

/**
 * Order management for administrators (ADM-5): listing every order and moving orders through
 * their statuses. Customers are notified of every change (ORD-9).
 */
public class OrderAdminService {

    private final OrderRepository orders;
    private final StockFacade stock;
    private final TransactionRunner transactions;
    private final Notifier notifier;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param orders       where orders are stored
     * @param stock        the catalog's stock, for returning copies on cancellation
     * @param transactions runs work in a transaction
     * @param notifier     tells customers about their orders
     * @param clock        the clock giving status change times
     */
    public OrderAdminService(OrderRepository orders, StockFacade stock, TransactionRunner transactions,
                             Notifier notifier, Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.transactions = transactions;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Returns one page of all orders, newest first.
     *
     * @param page which page to return
     * @return the page
     */
    public Page<OrderSummary> orders(PageRequest page) {
        return orders.findAll(page).map(OrderSummary::of);
    }

    /**
     * Returns one page of the orders in one status, newest first.
     *
     * @param status the status
     * @param page   which page to return
     * @return the page
     */
    public Page<OrderSummary> ordersIn(OrderStatus status, PageRequest page) {
        return orders.findByStatus(status, page).map(OrderSummary::of);
    }

    /**
     * Returns any order.
     *
     * @param orderNumber the order number
     * @return the order
     */
    public OrderDetails order(String orderNumber) {
        return OrderDetails.of(requireOrder(new OrderNumber(orderNumber)));
    }

    /**
     * Moves an order to another status. Cancelling puts the copies back into stock (ORD-7).
     *
     * @param orderNumber the order number
     * @param target      the new status
     * @return the updated order
     * @throws ConflictException if the change is not allowed from the current status (ORD-6)
     */
    public OrderDetails changeStatus(String orderNumber, OrderStatus target) {
        OrderNumber number = new OrderNumber(orderNumber);
        Order order = transactions.inTransaction(() -> {
            Order found = requireOrder(number);
            switch (target) {
                case PAID -> found.markPaid(clock);
                case SHIPPED -> found.ship(clock);
                case DELIVERED -> found.deliver(clock);
                case CANCELLED -> cancel(found);
                case CREATED -> found.status().requireCanMoveTo(OrderStatus.CREATED);
            }
            return orders.save(found);
        });
        order.pullEvents().forEach(notifier::publish);
        return OrderDetails.of(order);
    }

    private void cancel(Order order) {
        order.cancel(clock);
        stock.release(order.quantitiesByBook());
    }

    private Order requireOrder(OrderNumber number) {
        return orders.findByNumber(number).orElseThrow(() -> new NotFoundException("Order", number));
    }
}
