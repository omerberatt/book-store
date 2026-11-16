package com.bookstore.catalog.application;

import com.bookstore.catalog.domain.Category;
import com.bookstore.catalog.domain.CategoryId;

import java.util.List;

/**
 * A read-only copy of a category and everything below it, for menus.
 *
 * @param id       the category's id
 * @param name     the category's name
 * @param children the subcategories, in order
 */
public record CategoryNode(CategoryId id, String name, List<CategoryNode> children) {

    /** Copies the children. */
    public CategoryNode {
        children = List.copyOf(children);
    }

    /**
     * Copies a category tree recursively.
     *
     * @param category a stored category
     * @return the copy
     */
    public static CategoryNode of(Category category) {
        return new CategoryNode(category.id(), category.name(),
                category.children().stream().map(CategoryNode::of).toList());
    }
}
