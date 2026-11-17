package com.bookstore.shared.web;

import java.util.Map;

/**
 * What an endpoint answers: a status, an optional body written as JSON, and extra headers.
 *
 * @param status  the HTTP status code
 * @param body    the body, or {@code null} for none
 * @param headers extra headers, for example {@code Location}
 */
public record WebResponse(int status, Object body, Map<String, String> headers) {

    /** Copies the headers. */
    public WebResponse {
        headers = Map.copyOf(headers);
    }

    /**
     * Answers 200 with a body.
     *
     * @param body the body
     * @return the response
     */
    public static WebResponse ok(Object body) {
        return new WebResponse(HttpStatus.OK, body, Map.of());
    }

    /**
     * Answers 201 for a new resource.
     *
     * @param location the new resource's path
     * @param body     the new resource
     * @return the response
     */
    public static WebResponse created(String location, Object body) {
        return new WebResponse(HttpStatus.CREATED, body, Map.of("Location", location));
    }

    /** @return a 204 response without a body */
    public static WebResponse noContent() {
        return new WebResponse(HttpStatus.NO_CONTENT, null, Map.of());
    }
}
