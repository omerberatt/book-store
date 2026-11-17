package com.bookstore.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookstore.shared.jdbc.ModuleMigrations;
import com.bookstore.shared.jdbc.TestDatabase;
import com.bookstore.shared.kernel.event.DomainEvent;
import com.bookstore.shared.kernel.testing.TestClock;
import com.bookstore.shared.web.HttpApi;
import com.bookstore.shared.web.HttpTestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The whole application over HTTP, wired the way {@link BookstoreServer} wires it, on H2 with the
 * demo data: a new customer finds a book, orders it, the administrator delivers it, and the
 * customer reviews it. Only HTTP calls are used.
 */
class BookstoreHttpTest {

    private final TestDatabase database = TestDatabase.withModules(ModuleMigrations.ALL.toArray(String[]::new));
    private final List<DomainEvent> notified = Collections.synchronizedList(new ArrayList<>());
    private final HttpApi api;
    private final HttpTestClient visitor;
    private final HttpTestClient admin;

    BookstoreHttpTest() throws IOException {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1_000);
        BookstoreModule bookstore = new BookstoreModule(database.dataSource(), new BookstoreModule.Adapters(
                TestClock.CLOCK, DemoAdapters.approvingPayments(), hasher, notified::add));
        DemoData.load(database.dataSource(), bookstore, hasher, TestClock.CLOCK);
        api = BookstoreApi.of(bookstore).start(0);
        visitor = new HttpTestClient(api);
        admin = visitor.as(DemoData.ADMIN_EMAIL, DemoData.PASSWORD);
    }

    @AfterEach
    void stop() {
        api.close();
    }

    @Test
    void aNewCustomerBuysAndReviewsABook() {
        // A visitor finds Dune.
        JsonNode found = visitor.get("/api/v1/books?q=dune").json().get("content");
        assertThat(found.findValuesAsText("title")).containsExactly("Dune");
        long dune = found.get(0).get("id").asLong();
        assertThat(visitor.get("/api/v1/books/" + dune).json().get("language").asText()).isEqualTo("en");

        // The visitor registers and saves an address.
        assertThat(visitor.post("/api/v1/customers", """
                {"email": "deniz@example.com", "firstName": "Deniz", "lastName": "Kaya", "password": "okuma2026"}
                """).status()).isEqualTo(201);
        HttpTestClient deniz = visitor.as("deniz@example.com", "okuma2026");
        String address = deniz.post("/api/v1/me/addresses", """
                {"label": "Ev", "recipient": "Deniz Kaya", "phone": "05550000000", "line": "Okur Sk. 1",
                 "district": "Çankaya", "city": "Ankara", "postalCode": "06420", "makeDefault": true}
                """).location();
        long addressId = Long.parseLong(address.substring(address.lastIndexOf('/') + 1));

        // Two copies go into the cart and are ordered by card.
        deniz.post("/api/v1/cart/items", "{\"bookId\": " + dune + ", \"quantity\": 2}");
        HttpTestClient.Reply placed = deniz.post("/api/v1/orders",
                "{\"addressId\": " + addressId + ", \"paymentMethod\": \"CREDIT_CARD\"}");
        assertThat(placed.status()).isEqualTo(201);
        assertThat(placed.json().get("total").get("amount").decimalValue()).isEqualByComparingTo("700.00");
        String number = placed.json().get("orderNumber").asText();
        assertThat(visitor.get("/api/v1/books/" + dune).json().get("stock").asInt()).isEqualTo(7);

        // Not yet delivered: no review.
        assertThat(deniz.post("/api/v1/books/" + dune + "/reviews", "{\"stars\": 5}").status()).isEqualTo(403);

        // The administrator ships and delivers it; the customer cannot.
        String status = "/api/v1/admin/orders/" + number + "/status";
        assertThat(deniz.put(status, "{\"status\": \"SHIPPED\"}").status()).isEqualTo(403);
        admin.put(status, "{\"status\": \"SHIPPED\"}");
        assertThat(admin.put(status, "{\"status\": \"DELIVERED\"}").json().get("status").asText())
                .isEqualTo("DELIVERED");
        assertThat(deniz.get("/api/v1/orders/" + number).json().get("status").asText()).isEqualTo("DELIVERED");

        // Now the review is welcome and shows up in the book's rating.
        assertThat(deniz.post("/api/v1/books/" + dune + "/reviews", "{\"stars\": 5, \"comment\": \"Harika\"}")
                .status()).isEqualTo(201);
        JsonNode book = visitor.get("/api/v1/books/" + dune).json();
        assertThat(book.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(visitor.get("/api/v1/books/" + dune + "/reviews").json().get("content").get(0)
                .get("authorName").asText()).isEqualTo("Deniz K.");
        assertThat(notified).as("placed, paid, shipped, delivered").hasSize(4);
    }

    @Test
    void theDemoCustomerCanSignInAndStrangersCannot() {
        assertThat(visitor.as(DemoData.CUSTOMER_EMAIL, DemoData.PASSWORD).get("/api/v1/me").json()
                .get("addresses").size()).isEqualTo(1);
        assertThat(visitor.as(DemoData.CUSTOMER_EMAIL, "wrong").get("/api/v1/me").status()).isEqualTo(401);
        assertThat(visitor.get("/api/v1/cart").status()).isEqualTo(401);
    }

    @Test
    void theCatalogIsBrowsableWithoutSigningIn() {
        assertThat(visitor.get("/api/v1/books?size=50").json().get("totalElements").asInt()).isEqualTo(8);
        assertThat(visitor.get("/api/v1/categories").json().findValuesAsText("name"))
                .contains("Edebiyat", "Roman", "Bilim");
        assertThat(visitor.get("/api/v1/books?language=tr&inStock=true").json().get("content")
                .findValuesAsText("title")).doesNotContain("Benim Adım Kırmızı", "Dune");
    }
}
