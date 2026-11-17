package com.bookstore.review.rest;

import com.bookstore.review.api.ReviewCommand;
import com.bookstore.review.api.ReviewFacade;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.web.PageResponse;
import com.bookstore.shared.web.Routes;
import com.bookstore.shared.web.WebRequest;
import com.bookstore.shared.web.WebResponse;

import java.util.Map;
import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Reviews and ratings of a book (REV-1 – REV-6). Reading is open to everyone; writing needs a
 * signed-in customer who received the book. A customer has at most one review per book, so it is
 * addressed as {@code reviews/mine}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/v1/books/{id}/reviews}</td><td>the reviews, newest first</td></tr>
 *   <tr><td>{@code GET /api/v1/books/{id}/ratings}</td><td>how the ratings are spread</td></tr>
 *   <tr><td>{@code POST /api/v1/books/{id}/reviews}</td><td>write a review</td></tr>
 *   <tr><td>{@code GET|PUT|DELETE /api/v1/books/{id}/reviews/mine}</td><td>the customer's review</td></tr>
 *   <tr><td>{@code GET /api/v1/books/{id}/reviews/eligibility}</td><td>whether the customer may write one</td></tr>
 * </table>
 */
public final class ReviewResource implements Routes.Resource {

    private static final String MINE = "/api/v1/books/{id}/reviews/mine";

    private final ReviewFacade reviews;

    /**
     * Creates the resource.
     *
     * @param reviews the review use cases
     */
    public ReviewResource(ReviewFacade reviews) {
        this.reviews = Objects.requireNonNull(reviews, "reviews");
    }

    /**
     * {@code POST /api/v1/books/{id}/reviews} and {@code PUT .../reviews/mine}.
     *
     * @param stars   the rating, from 1 to 5
     * @param comment the comment, may be {@code null}
     */
    public record ReviewRequest(@Min(1) @Max(5) int stars, @Size(max = 2000) String comment) {
    }

    @Override
    public void register(Routes routes) {
        routes.get("/api/v1/books/{id}/reviews", request -> WebResponse.ok(
                        PageResponse.of(reviews.reviewsOf(book(request), PageResponse.pageOf(request)))))
                .get("/api/v1/books/{id}/ratings", request -> WebResponse.ok(reviews.ratingBreakdown(book(request))))
                .post("/api/v1/books/{id}/reviews", this::write)
                .get(MINE, request -> WebResponse.ok(reviews.myReview(me(request), book(request))
                        .orElseThrow(() -> new NotFoundException("Review", "of book " + book(request) + " by you"))))
                .put(MINE, this::edit)
                .delete(MINE, request -> {
                    reviews.delete(me(request), book(request));
                    return WebResponse.noContent();
                })
                .get("/api/v1/books/{id}/reviews/eligibility", request -> WebResponse.ok(
                        Map.of("canReview", reviews.canReview(me(request), book(request)))));
    }

    private WebResponse write(WebRequest request) {
        ReviewRequest review = request.body(ReviewRequest.class);
        BookId bookId = book(request);
        return WebResponse.created("/api/v1/books/" + bookId + "/reviews/mine",
                reviews.write(new ReviewCommand(me(request), bookId, review.stars(), review.comment())));
    }

    private WebResponse edit(WebRequest request) {
        ReviewRequest review = request.body(ReviewRequest.class);
        return WebResponse.ok(reviews.edit(new ReviewCommand(me(request), book(request), review.stars(),
                review.comment())));
    }

    private static BookId book(WebRequest request) {
        return BookId.of(request.pathId("id"));
    }

    private static CustomerId me(WebRequest request) {
        return request.customer().customerId();
    }
}
