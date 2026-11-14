package com.bookstore.application.port;

import com.bookstore.domain.event.DomainEvent;

/**
 * Tells customers about things that happened to them, such as a placed or shipped order.
 *
 * <p>Use cases call it only after their transaction has committed, so customers are never told
 * about something that was rolled back. Implementations must return quickly; the infrastructure
 * module queues events and handles them on background threads.
 */
public interface Notifier {

    /**
     * Hands over an event for notification.
     *
     * @param event the event
     */
    void publish(DomainEvent event);
}
