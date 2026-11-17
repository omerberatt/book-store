package com.bookstore.shared.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Talks to a running {@link HttpApi} with the JDK's {@link HttpClient}, the way a browser or
 * {@code curl} would. Shared with the modules' {@code rest} tests through the test-jar.
 */
public final class HttpTestClient {

    private final HttpClient client = HttpClient.newHttpClient();
    private final URI baseUri;
    private final String authorization;

    /**
     * Creates an anonymous client.
     *
     * @param api the running API
     */
    public HttpTestClient(HttpApi api) {
        this(api.baseUri(), null);
    }

    private HttpTestClient(URI baseUri, String authorization) {
        this.baseUri = baseUri;
        this.authorization = authorization;
    }

    /**
     * Returns a client that signs in with HTTP Basic.
     *
     * @param email    the user name
     * @param password the password
     * @return the client
     */
    public HttpTestClient as(String email, String password) {
        String token = Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return new HttpTestClient(baseUri, "Basic " + token);
    }

    /** Sends a {@code GET}. @param path the path and query @return the reply */
    public Reply get(String path) {
        return send("GET", path, null);
    }

    /** Sends a {@code POST}. @param path the path @param json the body, or {@code null} @return the reply */
    public Reply post(String path, String json) {
        return send("POST", path, json);
    }

    /** Sends a {@code PUT}. @param path the path @param json the body, or {@code null} @return the reply */
    public Reply put(String path, String json) {
        return send("PUT", path, json);
    }

    /** Sends a {@code DELETE}. @param path the path @return the reply */
    public Reply delete(String path) {
        return send("DELETE", path, null);
    }

    /**
     * Sends a request.
     *
     * @param method the method
     * @param path   the path and query
     * @param json   the body, or {@code null}
     * @return the reply
     */
    public Reply send(String method, String path, String json) {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try {
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Reply(response.statusCode(), response.body(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.headers().firstValue("Location").orElse(null));
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + path + " was interrupted", e);
        }
    }

    /**
     * An HTTP reply.
     *
     * @param status      the status code
     * @param body        the body as text
     * @param contentType the {@code Content-Type} header
     * @param location    the {@code Location} header, or {@code null}
     */
    public record Reply(int status, String body, String contentType, String location) {

        /** @return the body as a JSON tree */
        public JsonNode json() {
            try {
                return Json.mapper().readTree(body);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Not JSON: " + body, e);
            }
        }

        /** @return the problem's {@code code}, for error replies */
        public String code() {
            return json().path("code").asText();
        }
    }
}
