package com.bookstore.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;

/**
 * Brings a database schema up to date by running the SQL files in
 * {@code classpath:db/migration} that have not been run on it yet. Flyway records each applied
 * file in its own history table, so running the migrator again does nothing.
 */
public final class DatabaseMigrator {

    /** Where the migration files live on the class path. */
    public static final String LOCATION = "classpath:db/migration";

    private DatabaseMigrator() {
    }

    /**
     * Applies all pending migrations.
     *
     * @param dataSource the database to migrate
     * @return how many migrations were applied
     */
    public static int migrate(DataSource dataSource) {
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .load()
                .migrate();
        return result.migrationsExecuted;
    }
}
