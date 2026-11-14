package com.bookstore.persistence.catalog;

import com.bookstore.common.error.ConflictException;
import com.bookstore.domain.catalog.Category;
import com.bookstore.domain.catalog.CategoryRepository;
import com.bookstore.persistence.jdbc.DataAccessException;
import com.bookstore.persistence.jdbc.JdbcExecutor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores categories in the {@code categories} table. The tree is small, so it is always loaded in
 * one query and rebuilt in memory.
 */
public class JdbcCategoryRepository implements CategoryRepository {

    private final JdbcExecutor jdbc;

    /**
     * Creates the repository.
     *
     * @param jdbc runs the SQL
     */
    public JdbcCategoryRepository(JdbcExecutor jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Category> findRoots() {
        return loadTree().values().stream()
                .filter(Category::isRoot)
                .sorted(Comparator.comparing(Category::name))
                .toList();
    }

    @Override
    public Optional<Category> findById(long id) {
        return Optional.ofNullable(loadTree().get(id));
    }

    /**
     * Loads every category and links each subcategory to its parent.
     *
     * @return every category, keyed by id
     */
    Map<Long, Category> loadTree() {
        List<CategoryRow> rows = jdbc.query(
                "SELECT id, version, parent_id, name FROM categories "
                        + "ORDER BY CASE WHEN parent_id IS NULL THEN 0 ELSE 1 END, name, id",
                row -> {
                    // wasNull() reports on the column read last, so it must follow getLong("parent_id") directly.
                    long parentValue = row.getLong("parent_id");
                    Long parentId = row.wasNull() ? null : parentValue;
                    return new CategoryRow(row.getLong("id"), row.getInt("version"), parentId, row.getString("name"));
                });

        Map<Long, Category> byId = new HashMap<>();
        for (CategoryRow row : rows) {
            Category category = row.parentId() == null
                    ? Category.restoreRoot(row.id(), row.version(), row.name())
                    : Category.restoreChild(row.id(), row.version(), row.name(), byId.get(row.parentId()));
            byId.put(category.id(), category);
        }
        return byId;
    }

    @Override
    public Category save(Category category) {
        Long parentId = category.parent().map(parent -> {
            if (parent.isNew()) {
                throw new IllegalStateException("Store the parent category first");
            }
            return parent.id();
        }).orElse(null);
        try {
            if (category.isNew()) {
                long id = jdbc.insert("INSERT INTO categories (parent_id, name) VALUES (?, ?)", parentId,
                        category.name());
                category.markPersisted(id, 0);
            } else {
                jdbc.updateVersioned("Category " + category.id(),
                        "UPDATE categories SET name = ?, version = version + 1 WHERE id = ? AND version = ?",
                        category.name(), category.id(), category.version());
                category.markPersisted(category.id(), category.version() + 1);
            }
        } catch (DataAccessException e) {
            if (e.violates("uq_categories_parent_name")) {
                throw new ConflictException("category.name-taken",
                        "A category named '" + category.name() + "' already exists here");
            }
            throw e;
        }
        return category;
    }

    @Override
    public void delete(Category category) {
        jdbc.update("DELETE FROM categories WHERE id = ?", category.id());
    }

    private record CategoryRow(long id, int version, Long parentId, String name) {
    }
}
