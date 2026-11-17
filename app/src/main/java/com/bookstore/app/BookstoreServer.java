package com.bookstore.app;

import com.bookstore.shared.web.HttpApi;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Starts the Book Store API on the JDK HTTP server (Phase 5). Configuration comes from the
 * environment, never from the code:
 *
 * <ul>
 *   <li>{@code BOOKSTORE_PORT}: the port, {@code 8080} by default;</li>
 *   <li>{@code BOOKSTORE_DB_URL}, {@code BOOKSTORE_DB_USER}, {@code BOOKSTORE_DB_PASSWORD}: a
 *       PostgreSQL database, for example the one {@code docker compose up -d} starts. Without a URL
 *       the server uses an in-memory H2 database in PostgreSQL mode, which is lost when it stops;</li>
 *   <li>{@code BOOKSTORE_DEMO_DATA}: {@code true} to load the demo catalog and accounts into
 *       PostgreSQL; the in-memory database always gets them.</li>
 * </ul>
 *
 * <p>Run it with {@code ./mvnw -pl app exec:java} after {@code ./mvnw install -DskipTests}.
 */
public final class BookstoreServer {

    private static final System.Logger LOG = System.getLogger(BookstoreServer.class.getName());

    private BookstoreServer() {
    }

    /**
     * Starts the server and keeps it running until the process stops.
     *
     * @param args not used
     * @throws IOException if the port cannot be bound
     */
    public static void main(String[] args) throws IOException {
        String url = System.getenv("BOOKSTORE_DB_URL");
        DataSource dataSource = url == null ? inMemoryDatabase() : postgres(url);
        boolean demoData = url == null || Boolean.parseBoolean(System.getenv("BOOKSTORE_DEMO_DATA"));

        Clock clock = Clock.systemUTC();
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();
        BookstoreModule bookstore = new BookstoreModule(dataSource, new BookstoreModule.Adapters(clock,
                DemoAdapters.approvingPayments(), hasher, DemoAdapters.loggingNotifier()));
        int migrations = bookstore.migrate();
        LOG.log(Level.INFO, "Applied {0} migrations", migrations);
        if (demoData) {
            DemoData.load(dataSource, bookstore.accounts(), hasher, clock);
            LOG.log(Level.INFO, "Loaded the demo data; sign in as {0} or {1} with the password {2}",
                    DemoData.CUSTOMER_EMAIL, DemoData.ADMIN_EMAIL, DemoData.PASSWORD);
        }

        HttpApi api = BookstoreApi.of(bookstore).start(port());
        Runtime.getRuntime().addShutdownHook(new Thread(api::close, "bookstore-shutdown"));
        LOG.log(Level.INFO, "Try: curl {0}/api/v1/books", api.baseUri());
    }

    private static int port() {
        String port = System.getenv("BOOKSTORE_PORT");
        return port == null ? 8080 : Integer.parseInt(port);
    }

    private static DataSource inMemoryDatabase() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:bookstore-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        LOG.log(Level.INFO, "No BOOKSTORE_DB_URL; using an in-memory H2 database that is lost on exit");
        return h2;
    }

    private static DataSource postgres(String url) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(url);
        postgres.setUser(System.getenv("BOOKSTORE_DB_USER"));
        postgres.setPassword(System.getenv("BOOKSTORE_DB_PASSWORD"));
        return postgres;
    }
}
