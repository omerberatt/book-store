package com.bookstore.catalog.persistence;

import com.bookstore.catalog.domain.CategoryId;
import com.bookstore.catalog.domain.AuthorId;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.testing.InMemoryStore;
import com.bookstore.shared.kernel.error.ConflictException;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.domain.BookRepository;
import com.bookstore.catalog.domain.BookSearchCriteria;
import com.bookstore.catalog.domain.Isbn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Books in a map, with an extra index by ISBN. */
public class InMemoryBookRepository extends InMemoryStore<BookId, Book> implements BookRepository {

    private final Map<Isbn, BookId> idByIsbn = new HashMap<>();

    /** Creates an empty repository. */
    public InMemoryBookRepository() {
        super(BookId::new);
    }

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
    public boolean existsByAuthorId(AuthorId authorId) {
        return all().stream().flatMap(book -> book.authors().stream()).anyMatch(a -> a.id().equals(authorId));
    }

    @Override
    public boolean existsByCategoryId(CategoryId categoryId) {
        return all().stream().anyMatch(book -> book.category().id().equals(categoryId));
    }

    @Override
    protected void beforeSave(Book book) {
        BookId owner = idByIsbn.get(book.isbn());
        if (owner != null && (book.isNew() || !owner.equals(book.id()))) {
            throw new ConflictException("book.isbn-taken", "ISBN " + book.isbn() + " is already used");
        }
    }

    @Override
    protected void afterSave(Book book) {
        idByIsbn.put(book.isbn(), book.id());
    }
}
