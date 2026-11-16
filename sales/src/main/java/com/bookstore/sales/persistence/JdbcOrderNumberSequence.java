package com.bookstore.sales.persistence;

import com.bookstore.sales.domain.OrderNumberSequence;
import com.bookstore.sales.domain.OrderNumber;
import com.bookstore.shared.jdbc.DataAccessException;
import com.bookstore.shared.jdbc.JdbcExecutor;
import com.bookstore.shared.jdbc.JdbcTransactionRunner;

import java.time.Year;

/**
 * Hands out order numbers from a counter row per year in {@code order_number_sequences}.
 *
 * <p>Incrementing the counter with an {@code UPDATE} locks its row until the transaction ends, so
 * two checkouts running at the same time cannot get the same number: the second waits for the
 * first. If the transaction rolls back, its number is handed out again.
 */
public class JdbcOrderNumberSequence implements OrderNumberSequence {

    private final JdbcExecutor jdbc;
    private final JdbcTransactionRunner transactions;

    /**
     * Creates the sequence.
     *
     * @param jdbc         runs the SQL
     * @param transactions keeps the increment and the read in one transaction
     */
    public JdbcOrderNumberSequence(JdbcExecutor jdbc, JdbcTransactionRunner transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public OrderNumber next(Year year) {
        return transactions.inTransaction(() -> {
            if (!increment(year)) {
                try {
                    jdbc.update("INSERT INTO order_number_sequences (order_year, last_number) VALUES (?, 1)", year);
                } catch (DataAccessException e) {
                    // Another transaction created the row first; use it.
                    if (!e.isConstraintViolation() || !increment(year)) {
                        throw e;
                    }
                }
            }
            long number = jdbc.queryForLong("SELECT last_number FROM order_number_sequences WHERE order_year = ?",
                    year);
            return OrderNumber.of(year, number);
        });
    }

    private boolean increment(Year year) {
        return jdbc.update("UPDATE order_number_sequences SET last_number = last_number + 1 WHERE order_year = ?",
                year) == 1;
    }
}
