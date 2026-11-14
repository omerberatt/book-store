package com.bookstore.application.fake;

import com.bookstore.common.error.ConflictException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.customer.CustomerRepository;
import com.bookstore.domain.customer.Email;
import com.bookstore.domain.customer.SavedAddress;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Customers in a map, with an extra index by e-mail address. Saved addresses get their own ids. */
public class InMemoryCustomerRepository extends InMemoryStore<Customer> implements CustomerRepository {

    private final Map<Email, Long> idByEmail = new HashMap<>();
    private final AtomicLong addressSequence = new AtomicLong(1000);

    @Override
    public Optional<Customer> findByEmail(Email email) {
        return Optional.ofNullable(idByEmail.get(email)).flatMap(this::findById);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return idByEmail.containsKey(email);
    }

    @Override
    public Page<Customer> findAll(PageRequest page) {
        return Page.fromList(all().stream()
                .sorted(Comparator.comparing(Customer::registeredAt).thenComparing(Customer::id).reversed())
                .toList(), page);
    }

    @Override
    protected void beforeSave(Customer customer) {
        Long owner = idByEmail.get(customer.email());
        if (owner != null && (customer.isNew() || owner != customer.id())) {
            throw new ConflictException("email.taken", customer.email() + " is already registered");
        }
    }

    @Override
    protected void afterSave(Customer customer) {
        idByEmail.values().removeIf(id -> id == customer.id());
        idByEmail.put(customer.email(), customer.id());
        for (SavedAddress address : customer.addresses()) {
            if (address.isNew()) {
                address.markPersisted(addressSequence.incrementAndGet(), 0);
            }
        }
    }
}
