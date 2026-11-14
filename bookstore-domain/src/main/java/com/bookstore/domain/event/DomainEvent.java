package com.bookstore.domain.event;

import java.time.Instant;

/**
 * Something of business interest that has happened.
 *
 * <p>The hierarchy is {@code sealed}: every event type is listed here, so a {@code switch} over a
 * {@code DomainEvent} can be checked for completeness by the compiler.
 */
public sealed interface DomainEvent permits OrderPlaced, OrderStatusChanged {

    /**
     * Returns when the event happened.
     *
     * @return the moment of the event
     */
    Instant occurredAt();
}
