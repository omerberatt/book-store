package com.bookstore.application.admin;

import com.bookstore.application.order.OrderDetails;
import com.bookstore.application.order.OrderSummary;
import com.bookstore.application.port.Notifier;
import com.bookstore.application.port.TransactionRunner;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.BookRepository;
import com.bookstore.domain.order.Order;
import com.bookstore.domain.order.OrderLine;
import com.bookstore.domain.order.OrderNumber;
import com.bookstore.domain.order.OrderRepository;
import com.bookstore.domain.order.OrderStatus;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Order management for administrators (ADM-5): listing every order and moving orders through
 * their statuses. Customers are notified of every change (ORD-9).
 */
public class OrderAdminService {

    private final OrderRepository orders;
    private final BookRepository books;
    private final TransactionRunner transactions;
    private final Notifier notifier;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param orders       where orders are stored
     * @param books        where books are stored, for returning stock on cancellation
     * @param transactions runs work in a transaction
     * @param notifier     tells customers about their orders
     * @param clock        the clock giving status change times
     */
    public OrderAdminService(OrderRepository orders, BookRepository books, TransactionRunner transactions,
                             Notifier notifier, Clock clock) {
        this.orders = orders;
        this.books = books;
        this.transactions = transactions;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Returns one page of all orders, optionally in one status, newest first.
     *
     * @param status only orders in this status, or empty for all
     * @param page   which page to return
     * @return the page
     */
    public Page<OrderSummary> orders(Optional<OrderStatus> status, PageRequest page) {
        Page<Order> found = status.map(s -> orders.findByStatus(s, page)).orElseGet(() -> orders.findAll(page));
        return found.map(OrderSummary::of);
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
        Map<Long, Book> orderBooks = books.findAllById(order.lines().stream().map(OrderLine::bookId).toList())
                .stream()
                .collect(Collectors.toMap(Book::id, Function.identity()));
        order.cancel(orderBooks, clock);
        orderBooks.values().forEach(books::save);
    }

    private Order requireOrder(OrderNumber number) {
        return orders.findByNumber(number).orElseThrow(() -> new NotFoundException("Order", number));
    }
}
