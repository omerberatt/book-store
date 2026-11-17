package com.bookstore.shared.web;

import java.util.Optional;

import jakarta.ws.rs.core.SecurityContext;

/**
 * Finds the signed-in caller in a request's {@link SecurityContext}, where the
 * {@link BasicAuthenticationFilter} put it. Resources ask for the role they need.
 */
public final class Callers {

    private Callers() {
    }

    /**
     * Returns the caller, if they signed in.
     *
     * @param security the request's security context
     * @return the caller
     */
    public static Optional<Caller> of(SecurityContext security) {
        return security.getUserPrincipal() instanceof BasicAuthenticationFilter.CallerPrincipal principal
                ? Optional.of(principal.caller())
                : Optional.empty();
    }

    /**
     * Requires a signed-in caller.
     *
     * @param security the request's security context
     * @return the caller
     * @throws WebException 401 for an anonymous request
     */
    public static Caller customer(SecurityContext security) {
        return of(security).orElseThrow(WebException::unauthenticated);
    }

    /**
     * Requires a signed-in administrator.
     *
     * @param security the request's security context
     * @return the caller
     * @throws WebException 401 for an anonymous request, 403 for a customer
     */
    public static Caller administrator(SecurityContext security) {
        Caller caller = customer(security);
        if (!caller.isAdmin()) {
            throw WebException.forbidden();
        }
        return caller;
    }
}
