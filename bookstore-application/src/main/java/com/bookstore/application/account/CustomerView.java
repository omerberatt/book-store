package com.bookstore.application.account;

import com.bookstore.domain.customer.Address;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.customer.Role;
import com.bookstore.domain.customer.SavedAddress;

import java.time.Instant;
import java.util.List;

/**
 * A customer's profile page. The password hash is deliberately left out.
 *
 * @param id           the customer's id
 * @param email        the e-mail address
 * @param firstName    the first name
 * @param lastName     the last name
 * @param role         the role
 * @param registeredAt when the customer registered
 * @param addresses    the saved addresses
 */
public record CustomerView(long id, String email, String firstName, String lastName, Role role,
                           Instant registeredAt, List<AddressView> addresses) {

    /** Copies the addresses. */
    public CustomerView {
        addresses = List.copyOf(addresses);
    }

    /**
     * Copies a customer.
     *
     * @param customer a stored customer
     * @return the view
     */
    public static CustomerView of(Customer customer) {
        List<AddressView> addresses = customer.addresses().stream()
                .map(address -> AddressView.of(customer, address))
                .toList();
        return new CustomerView(customer.id(), customer.email().value(), customer.name().firstName(),
                customer.name().lastName(), customer.role(), customer.registeredAt(), addresses);
    }

    /**
     * A saved address.
     *
     * @param id        the address id, used to pick it at checkout
     * @param label     the customer's name for it
     * @param address   the address
     * @param isDefault whether it is pre-selected at checkout
     */
    public record AddressView(long id, String label, Address address, boolean isDefault) {

        /**
         * Copies a saved address.
         *
         * @param owner   the customer the address belongs to
         * @param address a stored address
         * @return the view
         */
        public static AddressView of(Customer owner, SavedAddress address) {
            boolean isDefault = owner.defaultAddress().map(address::equals).orElse(false);
            return new AddressView(address.id(), address.label(), address.address(), isDefault);
        }
    }
}
