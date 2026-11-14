package com.bookstore.persistence.catalog;

import com.bookstore.common.text.TextNormalizer;
import com.bookstore.domain.catalog.Author;
import com.bookstore.domain.catalog.AuthorRepository;
import com.bookstore.persistence.jdbc.JdbcExecutor;
import com.bookstore.persistence.jdbc.RowMapper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stores authors in the {@code authors} table, together with a normalised copy of the name used
 * by the catalog search.
 */
public class JdbcAuthorRepository implements AuthorRepository {

    static final RowMapper<Author> MAPPER =
            row -> Author.restore(row.getLong("id"), row.getInt("version"), row.getString("name"));

    private final JdbcExecutor jdbc;

    /**
     * Creates the repository.
     *
     * @param jdbc runs the SQL
     */
    public JdbcAuthorRepository(JdbcExecutor jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Author> findById(long id) {
        return jdbc.queryOne("SELECT id, version, name FROM authors WHERE id = ?", MAPPER, id);
    }

    @Override
    public List<Author> findAllById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT id, version, name FROM authors WHERE id IN (" + JdbcExecutor.placeholders(ids.size())
                + ")", MAPPER, JdbcExecutor.params(ids));
    }

    @Override
    public List<Author> findAll() {
        return jdbc.query("SELECT id, version, name FROM authors ORDER BY name_key, id", MAPPER);
    }

    @Override
    public Author save(Author author) {
        String key = TextNormalizer.searchKey(author.name());
        if (author.isNew()) {
            long id = jdbc.insert("INSERT INTO authors (name, name_key) VALUES (?, ?)", author.name(), key);
            author.markPersisted(id, 0);
        } else {
            jdbc.updateVersioned("Author " + author.id(),
                    "UPDATE authors SET name = ?, name_key = ?, version = version + 1 WHERE id = ? AND version = ?",
                    author.name(), key, author.id(), author.version());
            author.markPersisted(author.id(), author.version() + 1);
        }
        return author;
    }

    @Override
    public void delete(Author author) {
        jdbc.update("DELETE FROM authors WHERE id = ?", author.id());
    }
}
