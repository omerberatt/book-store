package com.bookstore.catalog.persistence;

import com.bookstore.catalog.domain.Category;
import com.bookstore.catalog.domain.CategoryId;
import com.bookstore.catalog.domain.CategoryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** The category tree kept as a list of roots. */
public class InMemoryCategoryRepository implements CategoryRepository {

    private final List<Category> roots = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public List<Category> findRoots() {
        return roots.stream().sorted(Comparator.comparing(Category::name)).toList();
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return roots.stream()
                .flatMap(root -> root.selfAndDescendants().stream())
                .filter(category -> category.id().equals(id))
                .findFirst();
    }

    @Override
    public Category save(Category category) {
        category.parent().ifPresent(parent -> {
            if (parent.isNew()) {
                throw new IllegalStateException("store the parent first");
            }
        });
        if (category.isNew()) {
            category.markPersisted(CategoryId.of(sequence.incrementAndGet()), 0);
            if (category.isRoot()) {
                roots.add(category);
            }
        } else {
            category.markPersisted(category.id(), category.version() + 1);
        }
        return category;
    }

    @Override
    public void delete(Category category) {
        roots.remove(category);
    }
}
