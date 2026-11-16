package com.bookstore.review.application;

import com.bookstore.review.api.RatingBreakdown;
import com.bookstore.review.api.ReviewView;
import com.bookstore.review.domain.Rating;
import com.bookstore.review.domain.RatingDistribution;
import com.bookstore.review.domain.Review;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates reviews into the {@code api} contract's views (Mapper, ADR-0003).
 */
final class ReviewMapper {

    private ReviewMapper() {
    }

    static ReviewView viewOf(Review review, String authorName) {
        return new ReviewView(review.id().value(), review.bookId(), review.customerId(), authorName,
                review.rating().stars(), review.comment(), review.createdAt(),
                review.updatedAt().isAfter(review.createdAt()));
    }

    static RatingBreakdown breakdownOf(RatingDistribution distribution) {
        List<Integer> counts = new ArrayList<>();
        for (int stars = Rating.MIN; stars <= Rating.MAX; stars++) {
            counts.add(distribution.count(Rating.of(stars)));
        }
        return new RatingBreakdown(counts, distribution.average().orElse(null));
    }
}
