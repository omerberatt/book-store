package com.bookstore.shared.web;

import com.bookstore.shared.kernel.identity.CustomerId;

import java.util.Objects;

/**
 * Who sent a request, as established by the {@link Authenticator}.
 *
 * @param customerId the signed-in customer
 * @param email      the e-mail address used to sign in
 * @param role       {@code CUSTOMER} or {@code ADMIN}
 */
public record Caller(CustomerId customerId, String email, String role) {

    /** The role that may use {@code /api/v1/admin/...}. */
    public static final String ADMIN = "ADMIN";

    /** Rejects missing parts. */
    public Caller {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
    }

    /** @return {@code true} for an administrator */
    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
