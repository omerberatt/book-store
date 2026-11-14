package com.bookstore.common.error;

/**
 * Signals that a request is valid on its own but conflicts with the current state, for example an
 * e-mail address that is already registered or an illegal order status change. Maps to HTTP 409.
 */
public class ConflictException extends BookstoreException {

    /**
     * Creates an exception with a specific error code.
     *
     * @param code    stable machine-readable code, for example {@code "email.taken"}
     * @param message human-readable description for logs
     */
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
