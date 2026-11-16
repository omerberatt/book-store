package com.bookstore.review.application;

import com.bookstore.catalog.api.RatingFacade;
import com.bookstore.customer.api.CustomerDirectory;
import com.bookstore.review.domain.Rating;
import com.bookstore.review.domain.RatingDistribution;
import com.bookstore.review.domain.Review;
import com.bookstore.review.domain.ReviewRepository;
import com.bookstore.sales.api.PurchaseHistory;
import com.bookstore.shared.kernel.error.AccessDeniedException;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.transaction.TransactionRunner;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writing, editing, deleting and listing reviews (REV-1 to REV-6).
 *
 * <p>Every change to a review also updates the book's rating totals in the catalog, in the same
 * transaction, so the average shown in lists always matches the reviews (ADR-0012). The catalog,
 * sales and customer modules are reached only through their {@code api} packages.
 */
public class ReviewService {

    /** Error code for a customer who has not received the book. */
    public static final String NOT_ELIGIBLE = "review.not-eligible";

    /** Error code for a second review of the same book. */
    public static final String DUPLICATE = "review.duplicate";

    private static final String UNKNOWN_AUTHOR = "—";

    private final ReviewRepository reviews;
    private final RatingFacade ratings;
    private final PurchaseHistory purchases;
    private final CustomerDirectory customers;
    private final TransactionRunner transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param reviews      where reviews are stored
     * @param ratings      the catalog's rating totals
     * @param purchases    the sales module's purchase history, to check who received a book
     * @param customers    the customer module's directory, for authors' names
     * @param transactions runs work in a transaction
     * @param clock        the clock giving review times
     */
    public ReviewService(ReviewRepository reviews, RatingFacade ratings, PurchaseHistory purchases,
                         CustomerDirectory customers, TransactionRunner transactions, Clock clock) {
        this.reviews = reviews;
        this.ratings = ratings;
        this.purchases = purchases;
        this.customers = customers;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Writes a review (REV-1 to REV-3).
     *
     * @param command the review form
     * @return the new review
     * @throws NotFoundException     if the book does not exist
     * @throws AccessDeniedException if the customer has not received the book
     * @throws ConflictException     if the customer has already reviewed the book
     */
    public ReviewView write(ReviewCommand command) {
        Rating rating = Rating.of(command.stars());
        return transactions.inTransaction(() -> {
            requireBook(command.bookId());
            if (!purchases.hasReceived(command.customerId(), command.bookId())) {
                throw new AccessDeniedException(NOT_ELIGIBLE, "Only customers who received the book can review it");
            }
            if (reviews.findByBookIdAndCustomerId(command.bookId(), command.customerId()).isPresent()) {
                throw new ConflictException(DUPLICATE, "The customer has already reviewed this book");
            }
            Review review = Review.write(command.bookId(), command.customerId(), rating, command.comment(), clock);
            ratings.addRating(command.bookId(), rating.stars());
            return view(reviews.save(review));
        });
    }

    /**
     * Changes the customer's review of a book (REV-3).
     *
     * @param command the review form
     * @return the updated review
     * @throws NotFoundException if the customer has not reviewed the book
     */
    public ReviewView edit(ReviewCommand command) {
        Rating rating = Rating.of(command.stars());
        return transactions.inTransaction(() -> {
            Review review = requireOwnReview(command.customerId(), command.bookId());
            ratings.removeRating(command.bookId(), review.rating().stars());
            review.edit(rating, command.comment(), clock);
            ratings.addRating(command.bookId(), rating.stars());
            return view(reviews.save(review));
        });
    }

    /**
     * Deletes the customer's review of a book (REV-3).
     *
     * @param customerId the signed-in customer
     * @param bookId     the reviewed book
     * @throws NotFoundException if the customer has not reviewed the book
     */
    public void delete(long customerId, long bookId) {
        transactions.run(() -> {
            Review review = requireOwnReview(customerId, bookId);
            ratings.removeRating(bookId, review.rating().stars());
            reviews.delete(review);
        });
    }

    /**
     * Returns one page of a book's reviews, newest first (REV-4).
     *
     * @param bookId the book's id
     * @param page   which page to return
     * @return the page
     */
    public Page<ReviewView> reviewsOf(long bookId, PageRequest page) {
        Page<Review> found = reviews.findByBookId(bookId, page);
        Set<Long> authorIds = found.content().stream().map(Review::customerId).collect(Collectors.toSet());
        Map<Long, String> names = customers.publicNames(authorIds);
        return found.map(review -> ReviewView.of(review, names.getOrDefault(review.customerId(), UNKNOWN_AUTHOR)));
    }

    /**
     * Returns how many ratings of each star value a book received, for its detail page (REV-6).
     *
     * @param bookId the book's id
     * @return the distribution, empty if nobody rated the book
     */
    public RatingDistribution ratingDistribution(long bookId) {
        return RatingDistribution.of(reviews.findRatingsByBookId(bookId));
    }

    /**
     * Returns the customer's own review of a book, for pre-filling the edit form.
     *
     * @param customerId the signed-in customer
     * @param bookId     the book's id
     * @return the review, or empty if the customer has not reviewed the book
     */
    public Optional<ReviewView> myReview(long customerId, long bookId) {
        return reviews.findByBookIdAndCustomerId(bookId, customerId).map(this::view);
    }

    /**
     * Tells whether the customer may write a review of a book, to decide whether to show the form.
     *
     * @param customerId the signed-in customer
     * @param bookId     the book's id
     * @return {@code true} if the customer received the book and has not reviewed it yet
     */
    public boolean canReview(long customerId, long bookId) {
        return purchases.hasReceived(customerId, bookId)
                && reviews.findByBookIdAndCustomerId(bookId, customerId).isEmpty();
    }

    private ReviewView view(Review review) {
        String name = customers.publicNames(List.of(review.customerId()))
                .getOrDefault(review.customerId(), UNKNOWN_AUTHOR);
        return ReviewView.of(review, name);
    }

    private Review requireOwnReview(long customerId, long bookId) {
        return reviews.findByBookIdAndCustomerId(bookId, customerId)
                .orElseThrow(() -> new NotFoundException("Review", "book " + bookId + " by customer " + customerId));
    }

    private void requireBook(long bookId) {
        if (!ratings.exists(bookId)) {
            throw new NotFoundException("Book", bookId);
        }
    }
}
