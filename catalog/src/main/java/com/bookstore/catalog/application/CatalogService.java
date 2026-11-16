package com.bookstore.catalog.application;

import com.bookstore.catalog.api.BookDetails;
import com.bookstore.catalog.api.BookSearchQuery;
import com.bookstore.catalog.api.BookSummary;
import com.bookstore.catalog.api.CatalogFacade;
import com.bookstore.catalog.api.CategoryNode;
import com.bookstore.catalog.domain.AuthorId;
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
 * Browsing and searching the catalog, as customers see it: deactivated books are invisible. Implements
 * the {@link CatalogFacade} contract; {@link CatalogMapper} turns books into its DTOs.
 */
public class CatalogService implements CatalogFacade {

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

    @Override
    public Page<BookSummary> search(BookSearchQuery query, PageRequest page) {
        Specification<Book> rule = BookSpecifications.active().and(BookSpecifications.matchingText(query.text()));
        if (query.inStockOnly()) {
            rule = rule.and(BookSpecifications.inStock());
        }
        if (query.categoryId() != null) {
            rule = rule.and(BookSpecifications.inCategories(categoryAndDescendantIds(CategoryId.of(query.categoryId()))));
        }
        if (query.authorId() != null) {
            rule = rule.and(BookSpecifications.byAuthor(AuthorId.of(query.authorId())));
        }
        if (query.minPrice() != null) {
            rule = rule.and(BookSpecifications.pricedFrom(query.minPrice()));
        }
        if (query.maxPrice() != null) {
            rule = rule.and(BookSpecifications.pricedUpTo(query.maxPrice()));
        }
        if (query.language() != null) {
            rule = rule.and(BookSpecifications.inLanguage(CatalogMapper.languageOf(query.language())));
        }
        return books.search(rule, CatalogMapper.sortOf(query), page).map(CatalogMapper::summaryOf);
    }

    /** The rating distribution (REV-6) comes from the review module, which depends on this one. */
    @Override
    public BookDetails book(BookId bookId) {
        Book book = books.findById(bookId)
                .filter(Book::isActive)
                .orElseThrow(() -> new NotFoundException("Book", bookId));
        return CatalogMapper.detailsOf(book);
    }

    @Override
    public List<CategoryNode> categoryTree() {
        return categories.findRoots().stream().map(CatalogMapper::nodeOf).toList();
    }

    private Set<CategoryId> categoryAndDescendantIds(CategoryId categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
        return category.selfAndDescendants().stream().map(Category::id).collect(Collectors.toSet());
    }
}
