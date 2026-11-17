package com.bookstore.shared.web;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Checks the <em>format</em> of request records with Bean Validation: required fields, lengths,
 * ranges (ADR-0009). Business rules stay in the domain. Every invalid field is reported at once.
 */
public final class RequestValidator {

    private static final Validator VALIDATOR = Validation.byProvider(HibernateValidator.class)
            .configure()
            // Messages without the Expression Language, in English whatever the server's locale.
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(Locale.ENGLISH), Locale.ENGLISH, false))
            .buildValidatorFactory()
            .getValidator();

    private RequestValidator() {
    }

    /**
     * Validates a request.
     *
     * @param request the request record
     * @param <T>     its type
     * @return the same request
     * @throws WebException 400 with every invalid field
     */
    public static <T> T validate(T request) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(request);
        if (!violations.isEmpty()) {
            List<Problem.FieldError> errors = violations.stream()
                    .map(v -> new Problem.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                    .sorted(Comparator.comparing(Problem.FieldError::field).thenComparing(Problem.FieldError::message))
                    .toList();
            throw WebException.invalid(errors);
        }
        return request;
    }
}
