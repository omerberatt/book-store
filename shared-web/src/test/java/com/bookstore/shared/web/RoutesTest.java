package com.bookstore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RoutesTest {

    private final Routes.Endpoint one = request -> WebResponse.ok("one");
    private final Routes.Endpoint mine = request -> WebResponse.ok("mine");
    private final Routes routes = new Routes()
            .get("/api/v1/books/{id}", one)
            .get("/api/v1/books/{id}/reviews/mine", mine)
            .get("/api/v1/books/{id}/reviews/{reviewId}", one);

    @Test
    void templatesBindTheirParameters() {
        Routes.Match match = routes.find("GET", "/api/v1/books/42");

        assertThat(match.endpoint()).isSameAs(one);
        assertThat(match.pathParameters()).isEqualTo(Map.of("id", "42"));
    }

    @Test
    void literalSegmentsWinOverParameters() {
        assertThat(routes.find("GET", "/api/v1/books/42/reviews/mine").endpoint()).isSameAs(mine);
        assertThat(routes.find("GET", "/api/v1/books/42/reviews/7").pathParameters())
                .containsEntry("reviewId", "7");
    }

    @Test
    void unknownPathsAre404AndWrongMethods405() {
        assertThatThrownBy(() -> routes.find("GET", "/api/v1/authors"))
                .isInstanceOfSatisfying(WebException.class, e -> assertThat(e.status()).isEqualTo(404));
        assertThatThrownBy(() -> routes.find("DELETE", "/api/v1/books/42"))
                .isInstanceOfSatisfying(WebException.class, e -> assertThat(e.status()).isEqualTo(405));
    }

    @Test
    void theSameRouteCannotBeRegisteredTwice() {
        assertThatThrownBy(() -> routes.get("/api/v1/books/{bookId}", one))
                .isInstanceOf(IllegalStateException.class);
    }
}
