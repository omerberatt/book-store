package com.bookstore.application.fake;

import com.bookstore.domain.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Shared storage for the in-memory repositories: assigns ids like an auto-increment column and
 * bumps the version on every save, like the JDBC repositories will.
 *
 * <p>Lookups by id use a {@link LinkedHashMap}, so they take constant time instead of scanning
 * every entity.
 *
 * @param <T> the entity type
 */
abstract class InMemoryStore<T extends Entity> {

    private final Map<Long, T> byId = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Returns the entity with an id, if stored. */
    public Optional<T> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Returns the stored entities among some ids. */
    public List<T> findAllById(Collection<Long> ids) {
        return ids.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Inserts or updates an entity, assigning an id on insert and a new version every time. */
    public T save(T entity) {
        beforeSave(entity);
        if (entity.isNew()) {
            entity.markPersisted(sequence.incrementAndGet(), 0);
        } else {
            entity.markPersisted(entity.id(), entity.version() + 1);
        }
        byId.put(entity.id(), entity);
        afterSave(entity);
        return entity;
    }

    /** Removes an entity. */
    public void delete(T entity) {
        byId.remove(entity.id());
    }

    /** Hook for uniqueness checks before an entity is stored. */
    protected void beforeSave(T entity) {
    }

    /** Hook for updating indexes after an entity is stored. */
    protected void afterSave(T entity) {
    }

    /** Returns every stored entity, in insertion order. */
    protected List<T> all() {
        return new ArrayList<>(byId.values());
    }

    /** Returns every stored entity that matches. */
    protected List<T> where(Predicate<? super T> condition) {
        return byId.values().stream().filter(condition).toList();
    }

    /** Returns the number of stored entities. */
    public int count() {
        return byId.size();
    }
}
