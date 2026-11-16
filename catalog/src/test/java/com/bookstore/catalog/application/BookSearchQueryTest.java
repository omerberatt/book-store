package com.bookstore.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.catalog.domain.BookSort;
import com.bookstore.shared.kernel.error.ValidationException;
import com.bookstore.shared.kernel.money.Money;

import org.junit.jupiter.api.Test;

class BookSearchQueryTest {

    @Test
    void rejectsAnInvertedPriceRange() {
        assertThatThrownBy(() -> BookSearchQuery.everything().priced(Money.of("100"), Money.of("50")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maxPrice");
    }

    @Test
    void anOpenEndedRangeIsFine() {
        assertThat(BookSearchQuery.everything().priced(null, Money.of("50")).maxPrice()).isEqualTo(Money.of("50"));
        assertThat(BookSearchQuery.everything().sort()).isEqualTo(BookSort.DEFAULT);
    }
}
