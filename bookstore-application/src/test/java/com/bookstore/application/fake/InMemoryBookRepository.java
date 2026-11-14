package com.bookstore.application.fake;

import com.bookstore.common.error.ConflictException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.BookRepository;
import com.bookstore.domain.catalog.BookSearchCriteria;
import com.bookstore.domain.catalog.Isbn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Books in a map, with an extra index by ISBN. */
public class InMemoryBookRepository extends InMemoryStore<Book> implements BookRepository {

    private final Map<Isbn, Long> idByIsbn = new HashMap<>();

    @Override
    public Optional<Book> findByIsbn(Isbn isbn) {
        return Optional.ofNullable(idByIsbn.get(isbn)).flatMap(this::findById);
    }

    @Override
    public Page<Book> search(BookSearchCriteria criteria, PageRequest page) {
        List<Book> matches = all().stream()
                .filter(criteria.asPredicate())
                .sorted(criteria.sort().comparator())
                .toList();
        return Page.fromList(matches, page);
    }

    @Override
    public boolean existsByAuthorId(long authorId) {
        return all().stream().flatMap(book -> book.authors().stream()).anyMatch(a -> a.id() == authorId);
    }

    @Override
    public boolean existsByCategoryId(long categoryId) {
        return all().stream().anyMatch(book -> book.category().id() == categoryId);
    }

    @Override
    protected void beforeSave(Book book) {
        Long owner = idByIsbn.get(book.isbn());
        if (owner != null && (book.isNew() || owner != book.id())) {
            throw new ConflictException("book.isbn-taken", "ISBN " + book.isbn() + " is already used");
        }
    }

    @Override
    protected void afterSave(Book book) {
        idByIsbn.put(book.isbn(), book.id());
    }
}
