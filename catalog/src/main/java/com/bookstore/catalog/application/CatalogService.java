package com.bookstore.catalog.application;

import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.domain.Entity;
import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.domain.BookRepository;
import com.bookstore.catalog.domain.BookSearchCriteria;
import com.bookstore.catalog.domain.Category;
import com.bookstore.catalog.domain.CategoryRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Browsing and searching the catalog, as customers see it: deactivated books are invisible.
 */
public class CatalogService {

    private final BookRepository books;
    private final CategoryRepository categories;

    /**
     * Creates the service.
     *
     * @param books      where books are stored
     * @param categories where categories are stored
     */
    public CatalogService(BookRepository books, CategoryRepository categories) {
        this.books = books;
        this.categories = categories;
    }

    /**
     * Searches the active books (CAT-1, CAT-5, SRC-1 to SRC-4).
     *
     * @param query what to look for
     * @param page  which page to return
     * @return one page of matching books
     * @throws NotFoundException if the query names a category that does not exist
     */
    public Page<BookSummary> search(BookSearchQuery query, PageRequest page) {
        BookSearchCriteria.Builder criteria = BookSearchCriteria.builder()
                .inStockOnly(query.inStockOnly())
                .sort(query.sort());
        query.text().ifPresent(criteria::text);
        query.categoryId().ifPresent(id -> criteria.categoryIds(categoryAndDescendantIds(id)));
        query.authorId().ifPresent(criteria::authorId);
        query.minPrice().ifPresent(criteria::minPrice);
        query.maxPrice().ifPresent(criteria::maxPrice);
        query.language().ifPresent(criteria::language);

        return books.search(criteria.build(), page).map(BookSummary::of);
    }

    /**
     * Returns a book's detail page (CAT-2, REV-5). The rating distribution (REV-6) comes from the
     * review module, which depends on this one and not the other way round.
     *
     * @param bookId the book's id
     * @return the details
     * @throws NotFoundException if the book does not exist or is deactivated (CAT-7)
     */
    public BookDetails book(long bookId) {
        Book book = books.findById(bookId)
                .filter(Book::isActive)
                .orElseThrow(() -> new NotFoundException("Book", bookId));
        return BookDetails.of(book);
    }

    /**
     * Returns the category tree for the navigation menu (CAT-4).
     *
     * @return the top-level categories with their subcategories
     */
    public List<CategoryNode> categoryTree() {
        return categories.findRoots().stream().map(CategoryNode::of).toList();
    }

    private Set<Long> categoryAndDescendantIds(long categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
        return category.selfAndDescendants().stream().map(Entity::id).collect(Collectors.toSet());
    }
}
