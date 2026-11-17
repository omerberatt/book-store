package com.bookstore.shared.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One HTTP request, as an endpoint sees it: path and query parameters, the JSON body and the
 * caller. Parameter readers turn a wrong format into a 400 problem, so endpoints never parse by
 * hand.
 */
public final class WebRequest {

    private final String method;
    private final String path;
    private final Map<String, String> pathParameters;
    private final Map<String, List<String>> queryParameters;
    private final byte[] body;
    private final Caller caller;

    /**
     * Creates a request. Called by {@link HttpApi}, and by tests.
     *
     * @param method          the HTTP method
     * @param path            the path, without the query
     * @param pathParameters  values of the path template's {@code {name}} segments
     * @param queryParameters the query parameters
     * @param body            the body, empty if there is none
     * @param caller          who sent it; {@code null} for an anonymous request
     */
    public WebRequest(String method, String path, Map<String, String> pathParameters,
                      Map<String, List<String>> queryParameters, byte[] body, Caller caller) {
        this.method = method;
        this.path = path;
        this.pathParameters = Map.copyOf(pathParameters);
        this.queryParameters = Map.copyOf(queryParameters);
        this.body = body.clone();
        this.caller = caller;
    }

    /** @return the HTTP method */
    public String method() {
        return method;
    }

    /** @return the path, without the query */
    public String path() {
        return path;
    }

    /**
     * Reads a path parameter.
     *
     * @param name the template segment's name, for example {@code "id"} for {@code {id}}
     * @return the value
     */
    public String pathParameter(String name) {
        String value = pathParameters.get(name);
        if (value == null) {
            throw new IllegalArgumentException("The route has no {" + name + "} segment");
        }
        return value;
    }

    /**
     * Reads a numeric id from the path.
     *
     * @param name the template segment's name
     * @return the value, greater than zero
     * @throws WebException 400 if it is not a positive number
     */
    public long pathId(String name) {
        return positive(name, pathParameter(name));
    }

    /**
     * Reads an optional query parameter.
     *
     * @param name the name
     * @return the first value, or {@code null} if it is missing or blank
     */
    public String query(String name) {
        List<String> values = queryParameters.get(name);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            return null;
        }
        return values.getFirst();
    }

    /**
     * Reads an optional numeric id from the query.
     *
     * @param name the name
     * @return the value, or {@code null} if missing
     * @throws WebException 400 if it is not a positive number
     */
    public Long queryId(String name) {
        String value = query(name);
        return value == null ? null : positive(name, value);
    }

    /**
     * Reads an optional whole number from the query.
     *
     * @param name     the name
     * @param fallback the value when it is missing
     * @return the value
     * @throws WebException 400 if it is not a whole number
     */
    public int queryInt(String name, int fallback) {
        String value = query(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw WebException.badRequest("request.bad-parameter", name + " must be a whole number");
        }
    }

    /**
     * Reads an optional yes/no flag from the query.
     *
     * @param name the name
     * @return {@code true} only for {@code true}
     * @throws WebException 400 for anything other than {@code true} or {@code false}
     */
    public boolean queryFlag(String name) {
        String value = query(name);
        if (value == null) {
            return false;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw WebException.badRequest("request.bad-parameter", name + " must be true or false");
        };
    }

    /**
     * Reads and validates the JSON body.
     *
     * @param type the request record, with Bean Validation annotations
     * @param <T>  its type
     * @return the request
     * @throws WebException 400 if the body is missing, malformed or has invalid fields
     */
    public <T> T body(Class<T> type) {
        return RequestValidator.validate(Json.read(body, type));
    }

    /** @return who sent the request, if they signed in */
    public Optional<Caller> caller() {
        return Optional.ofNullable(caller);
    }

    /**
     * Requires a signed-in caller.
     *
     * @return the caller
     * @throws WebException 401 for an anonymous request
     */
    public Caller customer() {
        return caller().orElseThrow(WebException::unauthenticated);
    }

    /**
     * Requires a signed-in administrator.
     *
     * @return the caller
     * @throws WebException 401 for an anonymous request, 403 for a customer
     */
    public Caller administrator() {
        Caller signedIn = customer();
        if (!signedIn.isAdmin()) {
            throw WebException.forbidden();
        }
        return signedIn;
    }

    private static long positive(String name, String value) {
        try {
            long number = Long.parseLong(value);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw WebException.badRequest("request.bad-parameter", name + " must be a positive whole number");
    }
}
