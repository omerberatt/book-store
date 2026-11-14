package com.bookstore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Starts the whole application on a random port and calls it over real HTTP.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BookstoreApplicationTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void homePageIsServedInTurkishByDefault() throws Exception {
        HttpResponse<String> response = get("/", "tr");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Kitap mağazasına hoş geldiniz.");
    }

    @Test
    void homePageIsServedInEnglishWhenRequested() throws Exception {
        HttpResponse<String> response = get("/", "en");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Welcome to the book store.");
    }

    private HttpResponse<String> get(String path, String language) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept-Language", language)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
