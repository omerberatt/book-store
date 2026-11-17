package com.bookstore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.identity.CustomerId;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The API end to end over HTTP: routing, JSON, validation, problems and HTTP Basic. */
class HttpApiTest {

    private final HttpApi api;
    private final HttpTestClient anonymous;

    HttpApiTest() throws IOException {
        Routes routes = new Routes()
                .get("/api/v1/things/{id}", request -> {
                    long id = request.pathId("id");
                    if (id == 404) {
                        throw new NotFoundException("Thing", id);
                    }
                    return WebResponse.ok(new Thing(BookId.of(id), "Dune", Instant.parse("2026-01-02T03:04:05Z")));
                })
                .post("/api/v1/things", request -> {
                    NewThing thing = request.body(NewThing.class);
                    return WebResponse.created("/api/v1/things/9", thing);
                })
                .get("/api/v1/me", request -> WebResponse.ok(request.customer()))
                .get("/api/v1/admin/things", request -> WebResponse.ok(request.administrator().email()))
                .get("/api/v1/conflict", request -> {
                    throw WebException.conflict("cart.changed", "The cart changed", Map.of("adjustments", List.of("x")));
                })
                .get("/api/v1/boom", request -> {
                    throw new IllegalStateException("database password is hunter2");
                });
        Authenticator authenticator = (user, password) -> switch (user + ":" + password) {
            case "ayse@example.com:secret" -> Optional.of(new Caller(CustomerId.of(1), user, "CUSTOMER"));
            case "admin@example.com:secret" -> Optional.of(new Caller(CustomerId.of(2), user, Caller.ADMIN));
            default -> Optional.empty();
        };
        api = new HttpApi(routes, authenticator).start(0);
        anonymous = new HttpTestClient(api);
    }

    @AfterEach
    void stop() {
        api.close();
    }

    @Test
    void writesRecordsWithIdsAsNumbersAndTimesAsText() {
        HttpTestClient.Reply reply = anonymous.get("/api/v1/things/7");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.contentType()).startsWith("application/json");
        assertThat(reply.json().get("id").asLong()).isEqualTo(7);
        assertThat(reply.json().get("at").asText()).isEqualTo("2026-01-02T03:04:05Z");
    }

    @Test
    void businessErrorsBecomeProblems() {
        HttpTestClient.Reply reply = anonymous.get("/api/v1/things/404");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.contentType()).startsWith(Problem.MEDIA_TYPE);
        assertThat(reply.code()).isEqualTo("not-found");
        assertThat(reply.json().get("type").asText()).isEqualTo("/problems/not-found");
    }

    @Test
    void everyInvalidFieldIsReportedAtOnce() {
        HttpTestClient.Reply reply = anonymous.post("/api/v1/things", "{\"name\": \" \", \"quantity\": 11}");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.code()).isEqualTo("request.invalid");
        assertThat(reply.json().get("errors").findValuesAsText("field")).containsExactly("name", "quantity");
    }

    @Test
    void validRequestsAreCreated() {
        HttpTestClient.Reply reply = anonymous.post("/api/v1/things", "{\"name\": \"Dune\", \"quantity\": 2}");

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.location()).isEqualTo("/api/v1/things/9");
    }

    @Test
    void malformedJsonAndUnknownFieldsAre400() {
        assertThat(anonymous.post("/api/v1/things", "{\"name\": ").code()).isEqualTo("request.malformed-json");
        assertThat(anonymous.post("/api/v1/things", "{\"name\": \"Dune\", \"quantity\": 1, \"price\": 0}").code())
                .isEqualTo("request.malformed-json");
        assertThat(anonymous.get("/api/v1/things/abc").status()).isEqualTo(400);
    }

    @Test
    void unknownRoutesAre404AndWrongMethods405() {
        assertThat(anonymous.get("/api/v1/nothing").status()).isEqualTo(404);
        assertThat(anonymous.delete("/api/v1/things/1").status()).isEqualTo(405);
    }

    @Test
    void protectedEndpointsNeedTheRightCaller() {
        assertThat(anonymous.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(anonymous.as("ayse@example.com", "wrong").get("/api/v1/me").status()).isEqualTo(401);
        assertThat(anonymous.as("ayse@example.com", "secret").get("/api/v1/me").json().get("customerId").asLong())
                .isEqualTo(1);
        assertThat(anonymous.as("ayse@example.com", "secret").get("/api/v1/admin/things").status()).isEqualTo(403);
        assertThat(anonymous.as("admin@example.com", "secret").get("/api/v1/admin/things").status()).isEqualTo(200);
    }

    @Test
    void problemsCarryExtensionMembers() {
        HttpTestClient.Reply reply = anonymous.get("/api/v1/conflict");

        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.code()).isEqualTo("cart.changed");
        assertThat(reply.json().get("adjustments").get(0).asText()).isEqualTo("x");
        assertThat(reply.json().has("extensions")).isFalse();
    }

    @Test
    void unexpectedFailuresAreOpaque500s() {
        HttpTestClient.Reply reply = anonymous.get("/api/v1/boom");

        assertThat(reply.status()).isEqualTo(500);
        assertThat(reply.body()).doesNotContain("hunter2");
    }

    record Thing(BookId id, String title, Instant at) {
    }

    record NewThing(@NotBlank String name, @Min(1) @Max(10) int quantity) {
    }
}
