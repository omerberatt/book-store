package com.bookstore.catalog.domain;

import static com.bookstore.catalog.domain.CatalogTestData.author;
import static com.bookstore.catalog.domain.CatalogTestData.bookBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.shared.kernel.error.ValidationException;
import com.bookstore.shared.kernel.money.Money;
import com.bookstore.shared.kernel.paging.SortOrder;

import java.time.Year;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BookSearchCriteriaTest {

    private final Book madonna = bookBuilder(1, "Kürk Mantolu Madonna", "120", 5)
            .author(author(10, "Sabahattin Ali"))
            .publicationYear(Year.of(1943))
            .build();
    private final Book dune = bookBuilder(2, "Dune", "350", 0)
            .language(Language.ENGLISH)
            .publicationYear(Year.of(1965))
            .build();
    private final Book calikusu = bookBuilder(3, "Çalıkuşu", "90", 2).build();
    private final Book hidden = bookBuilder(4, "Hidden", "50", 9).active(false).build();
    private final List<Book> books = List.of(madonna, dune, calikusu, hidden);

    @Test
    void emptyCriteriaMatchesEveryActiveBook() {
        assertThat(search(BookSearchCriteria.all())).containsExactlyInAnyOrder(madonna, dune, calikusu);
    }

    @Test
    void textMatchesTitlesIgnoringCaseAndTurkishLetters() {
        assertThat(search(criteria().text("CALIKUSU"))).containsExactly(calikusu);
        assertThat(search(criteria().text("mantolu"))).containsExactly(madonna);
    }

    @Test
    void textMatchesAuthorNames() {
        assertThat(search(criteria().text("sabahattın"))).containsExactly(madonna);
    }

    @Test
    void textMatchesIsbnFragmentsOfFourOrMoreDigits() {
        String isbn = dune.isbn().value();

        assertThat(search(criteria().text(isbn))).containsExactly(dune);
        assertThat(search(criteria().text("978"))).isEmpty();
    }

    @Test
    void blankTextIsIgnored() {
        BookSearchCriteria criteria = criteria().text("   ").build();

        assertThat(criteria.text()).isEmpty();
    }

    @Test
    void filtersByPriceLanguageStockAndCategory() {
        assertThat(search(criteria().minPrice(Money.of("100")).maxPrice(Money.of("350"))))
                .containsExactlyInAnyOrder(madonna, dune);
        assertThat(search(criteria().language(Language.ENGLISH))).containsExactly(dune);
        assertThat(search(criteria().inStockOnly(true))).containsExactlyInAnyOrder(madonna, calikusu);
        assertThat(search(criteria().categoryIds(Set.of(CategoryId.of(2))))).hasSize(3);
        assertThat(search(criteria().categoryIds(Set.of(CategoryId.of(99))))).isEmpty();
    }

    @Test
    void filtersByAuthor() {
        assertThat(search(criteria().authorId(AuthorId.of(10)))).containsExactly(madonna);
        assertThat(search(criteria().authorId(AuthorId.of(102)))).containsExactly(dune);
        assertThat(search(criteria().authorId(AuthorId.of(999)))).isEmpty();
    }

    @Test
    void inactiveBooksAreIncludedOnlyOnRequest() {
        assertThat(search(criteria().includeInactive(true))).contains(hidden);
    }

    @Test
    void rejectsAnInvertedPriceRange() {
        assertThatThrownBy(() -> criteria().minPrice(Money.of("100")).maxPrice(Money.of("50")).build())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maxPrice");
    }

    @Test
    void sortsByPriceInBothDirections() {
        assertThat(sorted(new BookSort(BookSortField.PRICE, SortOrder.ASC))).containsExactly(calikusu, madonna, dune);
        assertThat(sorted(new BookSort(BookSortField.PRICE, SortOrder.DESC))).containsExactly(dune, madonna, calikusu);
    }

    @Test
    void sortsByTitleIgnoringTurkishLetters() {
        assertThat(sorted(BookSort.DEFAULT)).containsExactly(calikusu, dune, madonna);
    }

    @Test
    void sortsByYearWithUnknownYearsFirst() {
        assertThat(sorted(new BookSort(BookSortField.PUBLICATION_YEAR, SortOrder.ASC)))
                .containsExactly(calikusu, madonna, dune);
    }

    @Test
    void sortsByRatingAndBreaksTiesByTitle() {
        madonna.addRating(5);
        dune.addRating(3);

        assertThat(sorted(new BookSort(BookSortField.RATING, SortOrder.DESC))).containsExactly(madonna, dune, calikusu);
        assertThat(sorted(new BookSort(BookSortField.RATING, SortOrder.ASC))).containsExactly(calikusu, dune, madonna);
    }

    private static BookSearchCriteria.Builder criteria() {
        return BookSearchCriteria.builder();
    }

    private List<Book> search(BookSearchCriteria.Builder builder) {
        return search(builder.build());
    }

    private List<Book> search(BookSearchCriteria criteria) {
        return books.stream().filter(criteria.asPredicate()).toList();
    }

    private List<Book> sorted(BookSort sort) {
        return books.stream().filter(Book::isActive).sorted(sort.comparator()).toList();
    }
}
