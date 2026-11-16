package com.bookstore.sales.application;

import com.bookstore.customer.api.CustomerDirectory;
import com.bookstore.shared.kernel.address.Address;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.testing.TestAddresses;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stands in for the customer module: customers with one saved address each, behind the
 * {@link CustomerDirectory} the sales module depends on.
 */
public final class FakeCustomers implements CustomerDirectory {

    private final Map<Long, Long> addressIdByCustomer = new HashMap<>();
    private final Map<Long, Address> addressById = new HashMap<>();
    private long nextCustomerId = 1;
    private long nextAddressId = 1001;

    /**
     * Registers a customer with one address.
     *
     * @return the customer's id
     */
    public long register() {
        long customerId = nextCustomerId++;
        long addressId = nextAddressId++;
        addressIdByCustomer.put(customerId, addressId);
        addressById.put(addressId, TestAddresses.home());
        return customerId;
    }

    /**
     * Returns the id of a customer's only address.
     *
     * @param customerId the customer's id
     * @return the address id
     */
    public long addressIdOf(long customerId) {
        return addressIdByCustomer.get(customerId);
    }

    @Override
    public Address shippingAddress(long customerId, long addressId) {
        Long own = addressIdByCustomer.get(customerId);
        if (own == null) {
            throw new NotFoundException("Customer", customerId);
        }
        if (own != addressId) {
            throw new NotFoundException("Address", addressId);
        }
        return addressById.get(addressId);
    }

    @Override
    public Map<Long, String> publicNames(Collection<Long> customerIds) {
        return customerIds.stream().filter(addressIdByCustomer::containsKey)
                .collect(Collectors.toMap(id -> id, id -> "Ayşe Y."));
    }
}
