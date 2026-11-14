package com.bookstore.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookstore.domain.catalog.Author;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EntityTest {

    @Test
    void newEntityHasNoIdUntilStored() {
        Author author = Author.create("Orhan Pamuk");

        assertThat(author.isNew()).isTrue();
        assertThat(author.version()).isZero();
        assertThatThrownBy(author::id).isInstanceOf(IllegalStateException.class);

        author.markPersisted(5, 1);

        assertThat(author.isNew()).isFalse();
        assertThat(author.id()).isEqualTo(5);
        assertThat(author.version()).isEqualTo(1);
    }

    @Test
    void idCannotChangeOnceAssignedButVersionCan() {
        Author author = Author.restore(5, 0, "Orhan Pamuk");

        author.markPersisted(5, 1);
        assertThat(author.version()).isEqualTo(1);

        assertThatThrownBy(() -> author.markPersisted(6, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> author.markPersisted(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storedEntitiesAreEqualByIdAndNewOnesOnlyToThemselves() {
        Author first = Author.restore(5, 0, "Orhan Pamuk");
        Author sameIdOtherName = Author.restore(5, 3, "O. Pamuk");
        Author newOne = Author.create("Orhan Pamuk");
        Author otherNewOne = Author.create("Orhan Pamuk");

        assertThat(first).isEqualTo(sameIdOtherName);
        assertThat(newOne).isEqualTo(newOne).isNotEqualTo(otherNewOne).isNotEqualTo(first);
    }

    @Test
    void hashCodeDoesNotChangeWhenTheIdIsAssigned() {
        Author author = Author.create("Elif Şafak");
        Set<Author> set = new HashSet<>(Set.of(author));

        author.markPersisted(9, 0);

        assertThat(set).contains(author);
    }
}
