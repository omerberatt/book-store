package com.bookstore.domain.catalog;

import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stores and finds books.
 */
public interface BookRepository {

    /**
     * Finds a book by id, active or not.
     *
     * @param id the id
     * @return the book, or empty
     */
    Optional<Book> findById(long id);

    /**
     * Finds a book by ISBN, active or not.
     *
     * @param isbn the ISBN
     * @return the book, or empty
     */
    Optional<Book> findByIsbn(Isbn isbn);

    /**
     * Finds several books at once. Ids with no book are skipped.
     *
     * @param ids the ids
     * @return the books found, in no particular order
     */
    List<Book> findAllById(Collection<Long> ids);

    /**
     * Returns one page of the books matching the criteria, in the criteria's order.
     *
     * @param criteria what to look for
     * @param page     which page to return
     * @return the page
     */
    Page<Book> search(BookSearchCriteria criteria, PageRequest page);

    /**
     * Tells whether any book, active or not, was written by the author.
     *
     * @param authorId the author's id
     * @return {@code true} if the author has books
     */
    boolean existsByAuthorId(long authorId);

    /**
     * Tells whether any book, active or not, is in the category.
     *
     * @param categoryId the category's id
     * @return {@code true} if the category has books
     */
    boolean existsByCategoryId(long categoryId);

    /**
     * Inserts a new book or updates a stored one.
     *
     * @param book the book
     * @return the same book, with its id and version updated
     * @throws com.bookstore.common.error.ConflictException if the ISBN is taken or the book was
     *                                                      changed by someone else in the meantime
     */
    Book save(Book book);
}
