package com.bookstore.shared.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * The JAX-RS exception mappers that answer every failure with an RFC 9457 problem (ADR-0009,
 * ADR-0010). Two mappers are needed: the runtime has its own mapper for Bean Validation, and only a
 * mapper for the more specific {@link ConstraintViolationException} takes precedence over it.
 */
public final class ProblemExceptionMappers {

    private static final ProblemMapper MAPPER = new ProblemMapper();

    private ProblemExceptionMappers() {
    }

    /**
     * Writes a problem as the response.
     *
     * @param problem the problem
     * @return the response
     */
    public static Response responseOf(Problem problem) {
        Response.ResponseBuilder response = Response.status(problem.status())
                .type(Problem.MEDIA_TYPE)
                .entity(problem.body());
        if (problem.status() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"bookstore\", charset=\"UTF-8\"");
        }
        return response.build();
    }

    /** Maps every exception that has no more specific mapper. */
    @Provider
    @ApplicationScoped
    public static class AnyFailure implements ExceptionMapper<Throwable> {

        @Override
        public Response toResponse(Throwable failure) {
            return responseOf(MAPPER.toProblem(failure));
        }
    }

    /** Maps the format errors Bean Validation found in a request record annotated with {@code @Valid}. */
    @Provider
    @ApplicationScoped
    public static class InvalidRequest implements ExceptionMapper<ConstraintViolationException> {

        @Override
        public Response toResponse(ConstraintViolationException failure) {
            return responseOf(MAPPER.toProblem(failure));
        }
    }
}
