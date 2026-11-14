package com.bookstore.domain.order;

import com.bookstore.common.money.Money;
import com.bookstore.common.validation.Guard;
import com.bookstore.domain.catalog.Book;

/**
 * One line of an order. The book's title and price are copied when the order is placed, so the
 * order keeps showing what the customer actually bought and paid, whatever happens to the book
 * later.
 *
 * @param bookId    the book's id
 * @param bookTitle the title at the time of the order
 * @param unitPrice the price of one copy at the time of the order
 * @param quantity  copies ordered
 */
public record OrderLine(long bookId, String bookTitle, Money unitPrice, int quantity) {

    /** Validates every part. */
    public OrderLine {
        Guard.require(bookId > 0, "bookId", "must be a stored book");
        bookTitle = Guard.requireText(bookTitle, "bookTitle", Book.MAX_TITLE_LENGTH);
        Guard.requireNonNull(unitPrice, "unitPrice");
        Guard.require(unitPrice.isPositive(), "unitPrice", "must be greater than zero");
        Guard.requirePositive(quantity, "quantity");
    }

    /**
     * Copies the current title and price of a book.
     *
     * @param book     a stored book
     * @param quantity copies ordered
     * @return the line
     */
    public static OrderLine of(Book book, int quantity) {
        return new OrderLine(book.id(), book.title(), book.price(), quantity);
    }

    /**
     * Returns the price of the whole line.
     *
     * @return unit price times quantity
     */
    public Money lineTotal() {
        return unitPrice.times(quantity);
    }
}
