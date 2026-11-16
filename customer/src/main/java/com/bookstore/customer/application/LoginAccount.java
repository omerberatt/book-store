package com.bookstore.customer.application;

import com.bookstore.customer.domain.Customer;
import com.bookstore.customer.domain.Role;
import com.bookstore.shared.kernel.identity.CustomerId;

/**
 * What the security layer needs to sign a customer in. The security layer checks the password
 * against {@link #passwordHash()} itself.
 *
 * @param customerId   the customer's id
 * @param email        the e-mail address used as the user name
 * @param passwordHash the stored password hash
 * @param role         the role
 * @param active       whether the customer may sign in (ACC-7)
 */
public record LoginAccount(CustomerId customerId, String email, String passwordHash, Role role, boolean active) {

    /**
     * Copies a customer.
     *
     * @param customer a stored customer
     * @return the account
     */
    public static LoginAccount of(Customer customer) {
        return new LoginAccount(customer.id(), customer.email().value(), customer.passwordHash(), customer.role(),
                customer.isActive());
    }

    /** Hides the password hash when the account is logged. */
    @Override
    public String toString() {
        return "LoginAccount[customerId=" + customerId + ", email=" + email + ", role=" + role + ", active="
                + active + "]";
    }
}
