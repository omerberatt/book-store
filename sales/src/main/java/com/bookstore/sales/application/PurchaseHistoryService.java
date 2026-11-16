package com.bookstore.sales.application;

import com.bookstore.sales.api.PurchaseHistory;
import com.bookstore.sales.domain.OrderRepository;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.identity.CustomerId;

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
    public boolean hasReceived(CustomerId customerId, BookId bookId) {
        return orders.hasDeliveredBook(customerId, bookId);
    }
}
