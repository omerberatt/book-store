package com.bookstore.customer.application;

import com.bookstore.customer.domain.AddressId;
import com.bookstore.customer.domain.Customer;
import com.bookstore.customer.domain.CustomerRepository;
import com.bookstore.customer.domain.Email;
import com.bookstore.customer.domain.PasswordHasher;
import com.bookstore.customer.domain.PasswordPolicy;
import com.bookstore.customer.domain.PersonName;
import com.bookstore.customer.domain.SavedAddress;
import com.bookstore.shared.kernel.address.Address;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.error.ValidationException;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.kernel.transaction.TransactionRunner;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Registration, sign-in lookup, profile and address book (ACC-1 to ACC-7).
 */
public class AccountService {

    /** Error code for an e-mail address that is already registered. */
    public static final String EMAIL_TAKEN = "email.taken";

    private final CustomerRepository customers;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param customers      where customers are stored
     * @param passwordHasher hashes and checks passwords
     * @param transactions   runs work in a transaction
     * @param clock          the clock giving registration times
     */
    public AccountService(CustomerRepository customers, PasswordHasher passwordHasher, TransactionRunner transactions,
                          Clock clock) {
        this.customers = customers;
        this.passwordHasher = passwordHasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Registers a new customer (ACC-1, ACC-2).
     *
     * @param command the registration form
     * @return the new customer's profile
     * @throws ValidationException if a field is invalid or the password breaks a rule
     * @throws ConflictException   if the e-mail address is already registered
     */
    public CustomerView register(RegistrationCommand command) {
        Email email = Email.of(command.email());
        PersonName name = new PersonName(command.firstName(), command.lastName());
        PasswordPolicy.check(command.password());

        return transactions.inTransaction(() -> {
            requireEmailFree(email);
            Customer customer = Customer.register(email, name, passwordHasher.hash(command.password()), clock);
            return CustomerView.of(customers.save(customer));
        });
    }

    /**
     * Looks up a customer by the e-mail address typed on the sign-in form (ACC-3).
     *
     * @param email the address as typed
     * @return the account, or empty if no customer has that address or it is not a valid address
     */
    public Optional<LoginAccount> findLoginAccount(String email) {
        Email parsed;
        try {
            parsed = Email.of(email);
        } catch (ValidationException e) {
            return Optional.empty();
        }
        return customers.findByEmail(parsed).map(LoginAccount::of);
    }

    /**
     * Returns a customer's profile.
     *
     * @param customerId the customer's id
     * @return the profile
     */
    public CustomerView profile(CustomerId customerId) {
        return CustomerView.of(requireCustomer(customerId));
    }

    /**
     * Changes a customer's name and e-mail address (ACC-4).
     *
     * @param customerId the customer's id
     * @param firstName  the new first name
     * @param lastName   the new last name
     * @param email      the new e-mail address
     * @return the updated profile
     * @throws ConflictException if another customer already uses the e-mail address
     */
    public CustomerView updateProfile(CustomerId customerId, String firstName, String lastName, String email) {
        PersonName name = new PersonName(firstName, lastName);
        Email newEmail = Email.of(email);
        return change(customerId, customer -> {
            if (!customer.email().equals(newEmail)) {
                requireEmailFree(newEmail);
                customer.changeEmail(newEmail);
            }
            customer.rename(name);
        });
    }

    /**
     * Changes a customer's password after checking the current one (ACC-4).
     *
     * @param customerId      the customer's id
     * @param currentPassword the current password, as typed
     * @param newPassword     the new password, as typed
     * @throws ValidationException if the current password is wrong or the new one breaks a rule
     */
    public void changePassword(CustomerId customerId, String currentPassword, String newPassword) {
        PasswordPolicy.check(newPassword);
        change(customerId, customer -> {
            if (!passwordHasher.matches(currentPassword, customer.passwordHash())) {
                throw new ValidationException("currentPassword", "is incorrect");
            }
            customer.changePasswordHash(passwordHasher.hash(newPassword));
        });
    }

    /**
     * Saves a new address (ACC-5).
     *
     * @param customerId  the customer's id
     * @param label       the customer's name for it
     * @param address     the address
     * @param makeDefault whether to make it the default
     * @return the updated profile, including the new address with its id
     */
    public CustomerView addAddress(CustomerId customerId, String label, Address address, boolean makeDefault) {
        return change(customerId, customer -> customer.addAddress(label, address, makeDefault));
    }

    /**
     * Changes a saved address.
     *
     * @param customerId the customer's id
     * @param addressId  the address id
     * @param label      the new label
     * @param address    the new address
     * @return the updated profile
     */
    public CustomerView changeAddress(CustomerId customerId, AddressId addressId, String label, Address address) {
        return change(customerId, customer -> customer.changeAddress(addressId, label, address));
    }

    /**
     * Makes a saved address the default.
     *
     * @param customerId the customer's id
     * @param addressId  the address id
     * @return the updated profile
     */
    public CustomerView makeDefaultAddress(CustomerId customerId, AddressId addressId) {
        return change(customerId, customer -> customer.makeDefault(addressId));
    }

    /**
     * Deletes a saved address. Orders placed with it keep their own copy.
     *
     * @param customerId the customer's id
     * @param addressId  the address id
     * @return the updated profile
     */
    public CustomerView removeAddress(CustomerId customerId, AddressId addressId) {
        return change(customerId, customer -> customer.removeAddress(addressId));
    }

    /**
     * Returns one saved address, for pre-filling the edit form.
     *
     * @param customerId the customer's id
     * @param addressId  the address id
     * @return the address
     */
    public CustomerView.AddressView address(CustomerId customerId, AddressId addressId) {
        Customer customer = requireCustomer(customerId);
        SavedAddress address = customer.address(addressId);
        return CustomerView.AddressView.of(customer, address);
    }

    private CustomerView change(CustomerId customerId, Consumer<Customer> operation) {
        return transactions.inTransaction(() -> {
            Customer customer = requireCustomer(customerId);
            operation.accept(customer);
            return CustomerView.of(customers.save(customer));
        });
    }

    private void requireEmailFree(Email email) {
        if (customers.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN, "The e-mail address " + email + " is already registered");
        }
    }

    private Customer requireCustomer(CustomerId customerId) {
        return customers.findById(customerId).orElseThrow(() -> new NotFoundException("Customer", customerId));
    }
}
