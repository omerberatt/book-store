package com.bookstore.sales.persistence;

import com.bookstore.shared.kernel.testing.InMemoryStore;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.sales.domain.Order;
import com.bookstore.sales.domain.OrderNumber;
import com.bookstore.sales.domain.OrderRepository;
import com.bookstore.sales.domain.OrderStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Orders in a map. Lists are sorted newest first. */
public class InMemoryOrderRepository extends InMemoryStore<Order> implements OrderRepository {

    private static final Comparator<Order> NEWEST_FIRST =
            Comparator.comparing(Order::placedAt).thenComparing(Order::id).reversed();

    @Override
    public Optional<Order> findByNumber(OrderNumber number) {
        return all().stream().filter(order -> order.number().equals(number)).findFirst();
    }

    @Override
    public Page<Order> findByCustomerId(long customerId, PageRequest page) {
        return newestFirst(order -> order.isOwnedBy(customerId), page);
    }

    @Override
    public Page<Order> findAll(PageRequest page) {
        return newestFirst(order -> true, page);
    }

    @Override
    public Page<Order> findByStatus(OrderStatus status, PageRequest page) {
        return newestFirst(order -> order.status() == status, page);
    }

    @Override
    public boolean hasDeliveredBook(long customerId, long bookId) {
        return all().stream().anyMatch(order -> order.isOwnedBy(customerId)
                && order.status() == OrderStatus.DELIVERED
                && order.contains(bookId));
    }

    private Page<Order> newestFirst(Predicate<Order> condition, PageRequest page) {
        List<Order> matches = where(condition).stream().sorted(NEWEST_FIRST).toList();
        return Page.fromList(matches, page);
    }
}
