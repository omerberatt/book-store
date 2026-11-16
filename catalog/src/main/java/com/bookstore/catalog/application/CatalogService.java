package com.bookstore.catalog.application;

import com.bookstore.catalog.domain.Book;
import com.bookstore.catalog.domain.BookRepository;
import com.bookstore.catalog.domain.BookSpecifications;
import com.bookstore.catalog.domain.Category;
import com.bookstore.catalog.domain.CategoryId;
import com.bookstore.catalog.domain.CategoryRepository;
import com.bookstore.shared.kernel.error.NotFoundException;
import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.paging.Page;
import com.bookstore.shared.kernel.paging.PageRequest;
import com.bookstore.shared.kernel.specification.Specification;

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
        Specification<Book> rule = BookSpecifications.active().and(BookSpecifications.matchingText(query.text()));
        if (query.inStockOnly()) {
            rule = rule.and(BookSpecifications.inStock());
        }
        if (query.categoryId() != null) {
            rule = rule.and(BookSpecifications.inCategories(categoryAndDescendantIds(query.categoryId())));
        }
        if (query.authorId() != null) {
            rule = rule.and(BookSpecifications.byAuthor(query.authorId()));
        }
        if (query.minPrice() != null) {
            rule = rule.and(BookSpecifications.pricedFrom(query.minPrice()));
        }
        if (query.maxPrice() != null) {
            rule = rule.and(BookSpecifications.pricedUpTo(query.maxPrice()));
        }
        if (query.language() != null) {
            rule = rule.and(BookSpecifications.inLanguage(query.language()));
        }
        return books.search(rule, query.sort(), page).map(BookSummary::of);
    }

    /**
     * Returns a book's detail page (CAT-2, REV-5). The rating distribution (REV-6) comes from the
     * review module, which depends on this one and not the other way round.
     *
     * @param bookId the book's id
     * @return the details
     * @throws NotFoundException if the book does not exist or is deactivated (CAT-7)
     */
    public BookDetails book(BookId bookId) {
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

    private Set<CategoryId> categoryAndDescendantIds(CategoryId categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
        return category.selfAndDescendants().stream().map(Category::id).collect(Collectors.toSet());
    }
}
