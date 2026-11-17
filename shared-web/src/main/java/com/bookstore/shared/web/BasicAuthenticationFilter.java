package com.bookstore.shared.web;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Objects;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

/**
 * Signs the caller in from the {@code Authorization: Basic} header before any resource runs
 * (ADR-0019): a request without the header stays anonymous, wrong credentials are a 401. The
 * caller is put into the request's {@link SecurityContext}, where {@link Callers} finds it. Phase 7
 * replaces this filter with Jakarta Security.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class BasicAuthenticationFilter implements ContainerRequestFilter {

    private final Authenticator authenticator;

    /**
     * Creates the filter.
     *
     * @param authenticator checks user names and passwords
     */
    @Inject
    public BasicAuthenticationFilter(Authenticator authenticator) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
    }

    /** For the CDI container's client proxy only; never called by the application. */
    protected BasicAuthenticationFilter() {
        this.authenticator = null;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String authorization = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return;
        }
        Caller caller = authenticate(authorization);
        boolean secure = request.getSecurityContext().isSecure();
        request.setSecurityContext(new CallerContext(caller, secure));
    }

    private Caller authenticate(String authorization) {
        if (!authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw WebException.unauthenticated();
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw WebException.unauthenticated();
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            throw WebException.unauthenticated();
        }
        return authenticator.authenticate(decoded.substring(0, colon), decoded.substring(colon + 1))
                .orElseThrow(WebException::unauthenticated);
    }

    /**
     * The signed-in caller as a JAX-RS principal.
     *
     * @param caller the caller
     */
    record CallerPrincipal(Caller caller) implements Principal {

        @Override
        public String getName() {
            return caller.email();
        }
    }

    /** A security context that knows the signed-in caller. */
    private record CallerContext(Caller caller, boolean secure) implements SecurityContext {

        @Override
        public Principal getUserPrincipal() {
            return new CallerPrincipal(caller);
        }

        @Override
        public boolean isUserInRole(String role) {
            return caller.role().equals(role);
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        @Override
        public String getAuthenticationScheme() {
            return SecurityContext.BASIC_AUTH;
        }
    }
}
