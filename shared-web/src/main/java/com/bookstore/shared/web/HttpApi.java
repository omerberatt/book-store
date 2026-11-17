package com.bookstore.shared.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The API on the JDK's HTTP server (ADR-0009, ADR-0019), doing by hand what an application server
 * does in Phase 6: it reads the request, signs the caller in from the {@code Authorization: Basic}
 * header, finds the endpoint, writes the answer as JSON and turns every failure into an RFC 9457
 * problem. Each request runs on its own virtual thread.
 */
public final class HttpApi implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(HttpApi.class.getName());
    private static final String JSON = "application/json";

    private final Routes routes;
    private final Authenticator authenticator;
    private final ProblemMapper problems = new ProblemMapper();
    private HttpServer server;
    private ExecutorService executor;

    /**
     * Creates the API; call {@link #start(int)} to serve it.
     *
     * @param routes        the endpoints
     * @param authenticator checks HTTP Basic credentials
     */
    public HttpApi(Routes routes, Authenticator authenticator) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
    }

    /**
     * Starts serving on the loopback interface.
     *
     * @param port the port, or 0 for any free port
     * @return this API
     * @throws IOException if the port cannot be bound
     */
    public synchronized HttpApi start(int port) throws IOException {
        if (server != null) {
            throw new IllegalStateException("The API is already running");
        }
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        LOG.log(Level.INFO, "Book Store API listening on {0}", baseUri());
        return this;
    }

    /** @return the port the API listens on */
    public int port() {
        return server.getAddress().getPort();
    }

    /** @return for example {@code http://127.0.0.1:8080} */
    public URI baseUri() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + port());
    }

    /** Stops serving; requests in progress get a second to finish. */
    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(1);
            executor.close();
            server = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            WebResponse response;
            try {
                response = dispatch(exchange);
            } catch (RuntimeException failure) {
                Problem problem = problems.toProblem(failure);
                if (problem.status() == HttpStatus.UNAUTHORIZED) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"bookstore\", charset=\"UTF-8\"");
                }
                send(exchange, problem.status(), Problem.MEDIA_TYPE, Json.write(problem));
                return;
            }
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
            byte[] body = response.body() == null ? new byte[0] : Json.write(response.body());
            send(exchange, response.status(), JSON, body);
        }
    }

    private WebResponse dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        Caller caller = authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
        Routes.Match match = routes.find(method, path);
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readAllBytes();
        }
        if (body.length > 0) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(JSON)) {
                throw WebException.unsupportedMediaType();
            }
        }
        WebRequest request = new WebRequest(method, path, match.pathParameters(), query(uri.getRawQuery()), body,
                caller);
        return match.endpoint().handle(request);
    }

    /** Signs the caller in; a request without credentials is anonymous, wrong credentials are a 401. */
    private Caller authenticate(String authorization) {
        if (authorization == null) {
            return null;
        }
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

    private static Map<String, List<String>> query(String rawQuery) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            parameters.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
        }
        return parameters;
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw WebException.badRequest("request.bad-query", "The query string is not correctly encoded");
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        if (body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
