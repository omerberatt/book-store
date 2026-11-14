package com.bookstore.application.fake;

import com.bookstore.common.error.ConflictException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.review.Rating;
import com.bookstore.domain.review.Review;
import com.bookstore.domain.review.ReviewRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Reviews in a map, enforcing one review per customer and book like a unique key would. */
public class InMemoryReviewRepository extends InMemoryStore<Review> implements ReviewRepository {

    private static final Comparator<Review> NEWEST_FIRST =
            Comparator.comparing(Review::createdAt).thenComparing(Review::id).reversed();

    @Override
    public Optional<Review> findByBookIdAndCustomerId(long bookId, long customerId) {
        return all().stream()
                .filter(review -> review.bookId() == bookId && review.isWrittenBy(customerId))
                .findFirst();
    }

    @Override
    public Page<Review> findByBookId(long bookId, PageRequest page) {
        return Page.fromList(where(review -> review.bookId() == bookId).stream().sorted(NEWEST_FIRST).toList(), page);
    }

    @Override
    public List<Rating> findRatingsByBookId(long bookId) {
        return where(review -> review.bookId() == bookId).stream().map(Review::rating).toList();
    }

    @Override
    public Page<Review> findAll(PageRequest page) {
        return Page.fromList(all().stream().sorted(NEWEST_FIRST).toList(), page);
    }

    @Override
    protected void beforeSave(Review review) {
        Optional<Review> existing = findByBookIdAndCustomerId(review.bookId(), review.customerId());
        if (existing.isPresent() && existing.get() != review) {
            throw new ConflictException("review.duplicate", "duplicate review");
        }
    }
}
