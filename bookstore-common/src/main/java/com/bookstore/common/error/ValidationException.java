package com.bookstore.common.error;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Signals that input does not satisfy a business rule, for example a negative price or a malformed
 * e-mail address. Maps to HTTP 400.
 *
 * <p>Carries one or more {@link Violation violations}, each naming the offending field, so that a
 * form can show the error next to the right input.
 */
public class ValidationException extends BookstoreException {

    /** Default error code for validation failures. */
    public static final String CODE = "validation.failed";

    private final List<Violation> violations;

    /**
     * Creates an exception for a single invalid field.
     *
     * @param field   name of the invalid field, as the caller knows it
     * @param message why the value is invalid
     */
    public ValidationException(String field, String message) {
        this(List.of(new Violation(field, message)));
    }

    /**
     * Creates an exception for several invalid fields.
     *
     * @param violations the individual problems, at least one
     */
    public ValidationException(List<Violation> violations) {
        super(CODE, describe(violations));
        this.violations = List.copyOf(violations);
    }

    /**
     * Returns the individual problems.
     *
     * @return an unmodifiable, non-empty list
     */
    public List<Violation> violations() {
        return violations;
    }

    private static String describe(List<Violation> violations) {
        Objects.requireNonNull(violations, "violations");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("at least one violation is required");
        }
        return violations.stream()
                .map(v -> v.field() + ": " + v.message())
                .collect(Collectors.joining("; "));
    }

    /**
     * One invalid field.
     *
     * @param field   name of the invalid field
     * @param message why the value is invalid
     */
    public record Violation(String field, String message) {

        /** Validates that both parts are present. */
        public Violation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
        }
    }
}
