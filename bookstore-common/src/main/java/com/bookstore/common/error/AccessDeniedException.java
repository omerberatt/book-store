package com.bookstore.common.error;

/**
 * Signals that the current user may not access a record that does exist, for example another
 * customer's order. Maps to HTTP 403.
 */
public class AccessDeniedException extends BookstoreException {

    /** Default error code for ownership violations. */
    public static final String CODE = "access-denied";

    /**
     * Creates an exception with a description of what was refused.
     *
     * @param message what the user tried to access
     */
    public AccessDeniedException(String message) {
        super(CODE, message);
    }
}
