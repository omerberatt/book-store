package com.bookstore.application.review;

import com.bookstore.application.port.TransactionRunner;
import com.bookstore.common.error.AccessDeniedException;
import com.bookstore.common.error.ConflictException;
import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.BookRepository;
import com.bookstore.domain.customer.Customer;
import com.bookstore.domain.customer.CustomerRepository;
import com.bookstore.domain.order.OrderRepository;
import com.bookstore.domain.review.Rating;
import com.bookstore.domain.review.Review;
import com.bookstore.domain.review.ReviewRepository;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writing, editing, deleting and listing reviews (REV-1 to REV-5).
 *
 * <p>Every change to a review also updates the book's rating totals in the same transaction, so
 * the average shown in lists always matches the reviews.
 */
public class ReviewService {

    /** Error code for a customer who has not received the book. */
    public static final String NOT_ELIGIBLE = "review.not-eligible";

    /** Error code for a second review of the same book. */
    public static final String DUPLICATE = "review.duplicate";

    private static final String UNKNOWN_AUTHOR = "—";

    private final ReviewRepository reviews;
    private final BookRepository books;
    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final TransactionRunner transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param reviews      where reviews are stored
     * @param books        where books are stored, for their rating totals
     * @param orders       where orders are stored, to check who received a book
     * @param customers    where customers are stored, for authors' names
     * @param transactions runs work in a transaction
     * @param clock        the clock giving review times
     */
    public ReviewService(ReviewRepository reviews, BookRepository books, OrderRepository orders,
                         CustomerRepository customers, TransactionRunner transactions, Clock clock) {
        this.reviews = reviews;
        this.books = books;
        this.orders = orders;
        this.customers = customers;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Writes a review (REV-1 to REV-3).
     *
     * @param command the review form
     * @return the new review
     * @throws AccessDeniedException if the customer has not received the book
     * @throws ConflictException     if the customer has already reviewed the book
     */
    public ReviewView write(ReviewCommand command) {
        Rating rating = Rating.of(command.stars());
        return transactions.inTransaction(() -> {
            Book book = requireBook(command.bookId());
            if (!orders.hasDeliveredBook(command.customerId(), command.bookId())) {
                throw new AccessDeniedException(NOT_ELIGIBLE, "Only customers who received the book can review it");
            }
            if (reviews.findByBookIdAndCustomerId(command.bookId(), command.customerId()).isPresent()) {
                throw new ConflictException(DUPLICATE, "The customer has already reviewed this book");
            }
            Review review = Review.write(command.bookId(), command.customerId(), rating, command.comment(), clock);
            book.addRating(rating.stars());
            books.save(book);
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
            Book book = requireBook(command.bookId());
            book.removeRating(review.rating().stars());
            review.edit(rating, command.comment(), clock);
            book.addRating(rating.stars());
            books.save(book);
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
            Book book = requireBook(bookId);
            book.removeRating(review.rating().stars());
            books.save(book);
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
        Map<Long, String> names = customers.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Customer::id, customer -> customer.name().publicName()));
        return found.map(review -> ReviewView.of(review, names.getOrDefault(review.customerId(), UNKNOWN_AUTHOR)));
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
        return orders.hasDeliveredBook(customerId, bookId)
                && reviews.findByBookIdAndCustomerId(bookId, customerId).isEmpty();
    }

    private ReviewView view(Review review) {
        String name = customers.findById(review.customerId())
                .map(customer -> customer.name().publicName())
                .orElse(UNKNOWN_AUTHOR);
        return ReviewView.of(review, name);
    }

    private Review requireOwnReview(long customerId, long bookId) {
        return reviews.findByBookIdAndCustomerId(bookId, customerId)
                .orElseThrow(() -> new NotFoundException("Review", "book " + bookId + " by customer " + customerId));
    }

    private Book requireBook(long bookId) {
        return books.findById(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
    }
}
