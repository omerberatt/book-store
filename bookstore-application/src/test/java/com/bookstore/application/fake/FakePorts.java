package com.bookstore.application.fake;

import com.bookstore.application.port.Notifier;
import com.bookstore.application.port.OrderNumberSequence;
import com.bookstore.application.port.PasswordHasher;
import com.bookstore.application.port.PaymentGateway;
import com.bookstore.application.port.TransactionRunner;
import com.bookstore.common.money.Money;
import com.bookstore.domain.event.DomainEvent;
import com.bookstore.domain.order.OrderNumber;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Simple stand-ins for the outgoing ports. */
public final class FakePorts {

    private FakePorts() {
    }

    /**
     * Runs work directly and counts how many transactions were started. It cannot roll anything
     * back: in-memory objects stay changed after an exception. Rollback is tested against a real
     * database in the persistence module.
     */
    public static final class DirectTransactions implements TransactionRunner {

        private int started;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            started++;
            return work.get();
        }

        /** @return the number of transactions started */
        public int started() {
            return started;
        }
    }

    /** "Hashes" by adding a prefix, so tests can see that the plain password was not stored. */
    public static final class PrefixPasswordHasher implements PasswordHasher {

        private static final String PREFIX = "{fake}";

        @Override
        public String hash(String rawPassword) {
            return PREFIX + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash.equals(PREFIX + rawPassword);
        }
    }

    /** Remembers every published event. */
    public static final class RecordingNotifier implements Notifier {

        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }

        /** @return the events published so far, in order */
        public List<DomainEvent> events() {
            return List.copyOf(events);
        }

        /** Forgets the events published so far. */
        public void clear() {
            events.clear();
        }
    }

    /** Approves every charge unless told to decline the next one. */
    public static final class ScriptedPaymentGateway implements PaymentGateway {

        private final List<Money> charged = new ArrayList<>();
        private String declineReason;

        @Override
        public PaymentResult charge(OrderNumber orderNumber, Money amount) {
            if (declineReason != null) {
                String reason = declineReason;
                declineReason = null;
                return new PaymentResult.Declined(reason);
            }
            charged.add(amount);
            return new PaymentResult.Approved("TX-" + orderNumber.value());
        }

        /** Makes the next charge fail. */
        public void declineNext(String reason) {
            this.declineReason = reason;
        }

        /** @return the amounts charged successfully, in order */
        public List<Money> charged() {
            return List.copyOf(charged);
        }
    }

    /** One counter per year, safe for concurrent use. */
    public static final class CountingOrderNumbers implements OrderNumberSequence {

        private final Map<Year, AtomicLong> counters = new ConcurrentHashMap<>();

        @Override
        public OrderNumber next(Year year) {
            long sequence = counters.computeIfAbsent(year, y -> new AtomicLong()).incrementAndGet();
            return OrderNumber.of(year, sequence);
        }
    }
}
