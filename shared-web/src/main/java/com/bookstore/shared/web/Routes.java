package com.bookstore.shared.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The API's endpoints, found by method and path. A path template has literal segments and
 * {@code {name}} segments, for example {@code /api/v1/books/{id}/reviews}; literal segments win
 * over parameters when two templates could both match.
 *
 * <p>An unknown path is a 404 problem; a known path with another method is a 405 problem.
 */
public final class Routes {

    private final List<Route> routes = new ArrayList<>();

    /**
     * Something that adds its endpoints, usually one module's {@code …Resource}.
     */
    @FunctionalInterface
    public interface Resource {

        /**
         * Adds the endpoints.
         *
         * @param routes where to add them
         */
        void register(Routes routes);
    }

    /**
     * Handles one endpoint's requests.
     */
    @FunctionalInterface
    public interface Endpoint {

        /**
         * Handles a request.
         *
         * @param request the request
         * @return the response
         */
        WebResponse handle(WebRequest request);
    }

    /**
     * A matched endpoint and the values of its path parameters.
     *
     * @param endpoint       the endpoint
     * @param pathParameters the parameters
     */
    public record Match(Endpoint endpoint, Map<String, String> pathParameters) {
    }

    /**
     * Adds a resource's endpoints.
     *
     * @param resource the resource
     * @return these routes
     */
    public Routes add(Resource resource) {
        resource.register(this);
        return this;
    }

    /** Adds a {@code GET} endpoint. @param template the path @param endpoint the handler @return these routes */
    public Routes get(String template, Endpoint endpoint) {
        return add("GET", template, endpoint);
    }

    /** Adds a {@code POST} endpoint. @param template the path @param endpoint the handler @return these routes */
    public Routes post(String template, Endpoint endpoint) {
        return add("POST", template, endpoint);
    }

    /** Adds a {@code PUT} endpoint. @param template the path @param endpoint the handler @return these routes */
    public Routes put(String template, Endpoint endpoint) {
        return add("PUT", template, endpoint);
    }

    /** Adds a {@code DELETE} endpoint. @param template the path @param endpoint the handler @return these routes */
    public Routes delete(String template, Endpoint endpoint) {
        return add("DELETE", template, endpoint);
    }

    /**
     * Adds an endpoint.
     *
     * @param method   the HTTP method
     * @param template the path template
     * @param endpoint the handler
     * @return these routes
     */
    public synchronized Routes add(String method, String template, Endpoint endpoint) {
        Route route = new Route(Objects.requireNonNull(method, "method"), segments(template),
                Objects.requireNonNull(endpoint, "endpoint"));
        boolean taken = routes.stream().anyMatch(r -> r.method.equals(method) && r.sameShapeAs(route));
        if (taken) {
            throw new IllegalStateException(method + " " + template + " is already registered");
        }
        routes.add(route);
        return this;
    }

    /**
     * Finds the endpoint for a request.
     *
     * @param method the HTTP method
     * @param path   the path, without the query
     * @return the endpoint and the path parameters
     * @throws WebException 404 for an unknown path, 405 for an unsupported method
     */
    public synchronized Match find(String method, String path) {
        List<String> segments = segments(path);
        Match best = null;
        int bestLiterals = -1;
        boolean pathKnown = false;
        for (Route route : routes) {
            Map<String, String> parameters = route.match(segments);
            if (parameters == null) {
                continue;
            }
            pathKnown = true;
            if (route.method.equals(method) && route.literals() > bestLiterals) {
                best = new Match(route.endpoint, parameters);
                bestLiterals = route.literals();
            }
        }
        if (best != null) {
            return best;
        }
        throw pathKnown ? WebException.methodNotAllowed(method, path) : WebException.noRoute(path);
    }

    private static List<String> segments(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private record Route(String method, List<String> template, Endpoint endpoint) {

        Map<String, String> match(List<String> path) {
            if (path.size() != template.size()) {
                return null;
            }
            Map<String, String> parameters = new HashMap<>();
            for (int i = 0; i < template.size(); i++) {
                String expected = template.get(i);
                if (isParameter(expected)) {
                    parameters.put(expected.substring(1, expected.length() - 1), path.get(i));
                } else if (!expected.equals(path.get(i))) {
                    return null;
                }
            }
            return parameters;
        }

        int literals() {
            return (int) template.stream().filter(segment -> !isParameter(segment)).count();
        }

        boolean sameShapeAs(Route other) {
            if (template.size() != other.template.size()) {
                return false;
            }
            for (int i = 0; i < template.size(); i++) {
                String mine = template.get(i);
                String theirs = other.template.get(i);
                if (isParameter(mine) != isParameter(theirs) || (!isParameter(mine) && !mine.equals(theirs))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isParameter(String segment) {
            return segment.startsWith("{") && segment.endsWith("}");
        }
    }
}
