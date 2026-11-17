package com.bookstore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookstore.shared.kernel.identity.BookId;
import com.bookstore.shared.kernel.money.Money;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void moneyIsWrittenAsAmountAndCurrencyOnly() {
        assertThat(text(Money.of("95.00"))).isEqualTo("{\"amount\":95.00,\"currency\":\"TRY\"}");
    }

    @Test
    void typedIdsAreNumbers() {
        assertThat(text(new Line(BookId.of(7), true))).isEqualTo("{\"bookId\":7,\"inStock\":true}");
    }

    private static String text(Object value) {
        return new String(Json.write(value), StandardCharsets.UTF_8);
    }

    record Line(BookId bookId, boolean inStock) {
    }
}
