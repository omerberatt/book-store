package com.bookstore.customer.rest;

import com.bookstore.customer.api.AccountFacade;
import com.bookstore.shared.web.Authenticator;
import com.bookstore.shared.web.Caller;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Signs HTTP Basic requests in with the customer module's accounts (ADR-0019): the e-mail address
 * is the user name, the password is checked by {@link AccountFacade#authenticate}.
 */
@ApplicationScoped
public class AccountAuthenticator implements Authenticator {

    private final AccountFacade accounts;

    /**
     * Creates the authenticator.
     *
     * @param accounts the account use cases
     */
    @Inject
    public AccountAuthenticator(AccountFacade accounts) {
        this.accounts = accounts;
    }

    /** For the CDI container's client proxy only; never called by the application. */
    protected AccountAuthenticator() {
        this(null);
    }

    @Override
    public Optional<Caller> authenticate(String username, String password) {
        return accounts.authenticate(username, password)
                .map(account -> new Caller(account.customerId(), account.email(), account.role()));
    }
}
