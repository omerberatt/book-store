package com.bookstore.sales.application;

import com.bookstore.sales.api.PurchaseHistory;
import com.bookstore.sales.domain.OrderRepository;

/**
 * Answers other modules' questions about purchases.
 */
public class PurchaseHistoryService implements PurchaseHistory {

    private final OrderRepository orders;

    /**
     * Creates the service.
     *
     * @param orders where orders are stored
     */
    public PurchaseHistoryService(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public boolean hasReceived(long customerId, long bookId) {
        return orders.hasDeliveredBook(customerId, bookId);
    }
}
