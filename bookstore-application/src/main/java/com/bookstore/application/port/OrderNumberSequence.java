package com.bookstore.application.port;

import com.bookstore.domain.order.OrderNumber;

import java.time.Year;

/**
 * Hands out order numbers. Numbers are unique and increase within a year; the sequence restarts at
 * 1 every year. A number taken by a transaction that later rolls back is not reused, so gaps are
 * possible.
 */
public interface OrderNumberSequence {

    /**
     * Returns the next free order number for a year. Safe to call from several threads at once.
     *
     * @param year the year the order is placed in
     * @return a number no other caller has received
     */
    OrderNumber next(Year year);
}
