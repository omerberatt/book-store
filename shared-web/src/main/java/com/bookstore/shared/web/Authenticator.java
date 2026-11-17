package com.bookstore.shared.web;

import java.util.Optional;

/**
 * Checks the user name and password of an HTTP Basic request (ADR-0019). The {@code app} module
 * implements it with the customer module's {@code AccountFacade}; Phase 7 replaces it with
 * Jakarta Security.
 */
@FunctionalInterface
public interface Authenticator {

    /** An authenticator that knows nobody, for APIs without protected endpoints. */
    Authenticator NOBODY = (username, password) -> Optional.empty();

    /**
     * Checks credentials.
     *
     * @param username the e-mail address
     * @param password the password
     * @return the caller, or empty if the credentials are wrong or the account is disabled
     */
    Optional<Caller> authenticate(String username, String password);
}
