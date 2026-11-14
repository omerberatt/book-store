package com.bookstore.application.order;

import com.bookstore.common.error.AccessDeniedException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.order.Order;
import com.bookstore.domain.order.OrderNumber;
import com.bookstore.domain.order.OrderRepository;

/**
 * A customer's own orders (ORD-8).
 */
public class OrderService {

    private final OrderRepository orders;

    /**
     * Creates the service.
     *
     * @param orders where orders are stored
     */
    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    /**
     * Returns one page of a customer's orders, newest first.
     *
     * @param customerId the signed-in customer
     * @param page       which page to return
     * @return the page
     */
    public Page<OrderSummary> myOrders(long customerId, PageRequest page) {
        return orders.findByCustomerId(customerId, page).map(OrderSummary::of);
    }

    /**
     * Returns one of the customer's orders.
     *
     * @param customerId  the signed-in customer
     * @param orderNumber the order number
     * @return the order
     * @throws NotFoundException     if there is no such order
     * @throws AccessDeniedException if the order belongs to someone else
     */
    public OrderDetails myOrder(long customerId, String orderNumber) {
        OrderNumber number = new OrderNumber(orderNumber);
        Order order = orders.findByNumber(number).orElseThrow(() -> new NotFoundException("Order", number));
        if (!order.isOwnedBy(customerId)) {
            throw new AccessDeniedException("Order " + number + " belongs to another customer");
        }
        return OrderDetails.of(order);
    }
}
