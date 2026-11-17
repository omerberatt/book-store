package com.bookstore.sales.application;

import com.bookstore.sales.domain.OrderEvent;
import com.bookstore.shared.kernel.event.Notifier;

import java.util.function.Consumer;

/**
 * Tells customers about their orders (ADR-0007): it observes the sales module's {@link OrderEvent}s
 * and hands them to the {@link Notifier} adapter. A new reaction to orders is a new observer like
 * this one; the use cases do not change.
 */
public final class OrderNotificationObserver implements Consumer<OrderEvent> {

    private final Notifier notifier;

    /**
     * Creates the observer.
     *
     * @param notifier the adapter that reaches the customer
     */
    public OrderNotificationObserver(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void accept(OrderEvent event) {
        notifier.publish(event);
    }
}
