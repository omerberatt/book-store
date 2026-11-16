package com.bookstore.shared.kernel.error;

import java.util.Objects;

/**
 * Root of every business error raised by the application.
 *
 * <p>It is unchecked so that use cases do not have to declare it, and abstract so that callers
 * always throw one of the specific subtypes. Each instance carries a short, stable machine-readable
 * {@link #code() code} (for example {@code "order.illegal-transition"}) that the web layer uses to
 * pick a translated message and an HTTP status.
 */
public abstract class BookstoreException extends RuntimeException {

    private final String code;

    /**
     * Creates an exception with an error code and an English developer-facing message.
     *
     * @param code    stable machine-readable error code, never blank
     * @param message human-readable description for logs
     */
    protected BookstoreException(String code, String message) {
        super(message);
        this.code = requireCode(code);
    }

    /**
     * Creates an exception that wraps a lower-level cause.
     *
     * @param code    stable machine-readable error code, never blank
     * @param message human-readable description for logs
     * @param cause   the underlying technical error
     */
    protected BookstoreException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = requireCode(code);
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return the error code
     */
    public String code() {
        return code;
    }

    private static String requireCode(String code) {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code;
    }
}
