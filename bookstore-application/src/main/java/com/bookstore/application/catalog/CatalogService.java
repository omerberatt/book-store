package com.bookstore.application.catalog;

import com.bookstore.common.error.NotFoundException;
import com.bookstore.common.paging.Page;
import com.bookstore.common.paging.PageRequest;
import com.bookstore.domain.Entity;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.BookRepository;
import com.bookstore.domain.catalog.BookSearchCriteria;
import com.bookstore.domain.catalog.Category;
import com.bookstore.domain.catalog.CategoryRepository;
import com.bookstore.domain.review.RatingDistribution;
import com.bookstore.domain.review.ReviewRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Browsing and searching the catalog, as customers see it: deactivated books are invisible.
 */
public class CatalogService {

    private final BookRepository books;
    private final CategoryRepository categories;
    private final ReviewRepository reviews;

    /**
     * Creates the service.
     *
     * @param books      where books are stored
     * @param categories where categories are stored
     * @param reviews    where reviews are stored, for rating distributions
     */
    public CatalogService(BookRepository books, CategoryRepository categories, ReviewRepository reviews) {
        this.books = books;
        this.categories = categories;
        this.reviews = reviews;
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
     * Returns a book's detail page (CAT-2, REV-5, REV-6).
     *
     * @param bookId the book's id
     * @return the details
     * @throws NotFoundException if the book does not exist or is deactivated (CAT-7)
     */
    public BookDetails book(long bookId) {
        Book book = books.findById(bookId)
                .filter(Book::isActive)
                .orElseThrow(() -> new NotFoundException("Book", bookId));
        RatingDistribution distribution = RatingDistribution.of(reviews.findRatingsByBookId(bookId));
        return BookDetails.of(book, distribution);
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
