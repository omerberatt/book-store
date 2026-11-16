package com.bookstore.catalog.application;

import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.catalog.api.RatingFacade;
import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.domain.BookRepository;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.transaction.TransactionRunner;

import java.util.function.Consumer;

/**
 * Keeps the rating totals on books up to date for the review module.
 */
public class RatingService implements RatingFacade {

    private final BookRepository books;
    private final TransactionRunner transactions;

    /**
     * Creates the service.
     *
     * @param books        where books are stored
     * @param transactions runs work in a transaction; joins the caller's if there is one
     */
    public RatingService(BookRepository books, TransactionRunner transactions) {
        this.books = books;
        this.transactions = transactions;
    }

    @Override
    public boolean exists(BookId bookId) {
        return books.findById(bookId).isPresent();
    }

    @Override
    public void addRating(BookId bookId, int stars) {
        change(bookId, book -> book.addRating(stars));
    }

    @Override
    public void removeRating(BookId bookId, int stars) {
        change(bookId, book -> book.removeRating(stars));
    }

    private void change(BookId bookId, Consumer<Book> operation) {
        transactions.run(() -> {
            Book book = books.findById(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
            operation.accept(book);
            books.save(book);
        });
    }
}
