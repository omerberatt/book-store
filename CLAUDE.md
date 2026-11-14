# Book Store

Layered, multi-module Java web application. Its second goal is to exercise the whole undergraduate Java curriculum, so fundamentals are hand-written on purpose.

Read before non-trivial work: `docs/SPEC.md` (what), `docs/ARCHITECTURE.md` (how), `docs/CURRICULUM-MAP.md` (which topic goes where), `docs/ROADMAP.md` (phase order).

## Commands

- Build and test everything: `./mvnw -B -ntp verify`
- One module with its dependencies: `./mvnw -B -ntp -pl bookstore-domain -am verify`
- Run the app: `./mvnw -pl bookstore-web -am install -DskipTests` then `./mvnw -pl bookstore-web spring-boot:run`

Requires JDK 21. Maven comes from the wrapper.

## Architecture rules

- Dependencies point inward: `web → application → domain → common`. `persistence` and `infrastructure` depend on `application` and never on each other.
- `bookstore-common`, `bookstore-domain` and `bookstore-application` use the JDK only (plus the SLF4J API). No Spring, Servlet, JDBC or Jackson types there, and no Spring annotations. They are wired as `@Bean`s in `com.bookstore.web.config`.
- Repository interfaces live in `domain`; outgoing ports that are not repositories live in `application.port`. Implementations go in `persistence` (JDBC) or `infrastructure`.
- Data access is hand-written JDBC with `PreparedStatement`. Do not add JPA, Spring Data or `JdbcTemplate`.
- Schema changes go through Flyway files in `bookstore-persistence/src/main/resources/db/migration`, written in SQL that runs on both MySQL and H2 (MySQL mode).
- Packages are organised by feature (`catalog`, `cart`, `order`, `customer`, `review`), not by layer.

## Code conventions

- Code, identifiers, commit messages and table names in English. Documents under `docs/` and the README in Turkish.
- Value objects are `record`s and validate in the constructor. Entities protect their own invariants.
- Money is always `Money` (`BigDecimal`), never `double`. Time is always `java.time`, with an injected `Clock`.
- Return `Optional` or an empty collection, never `null`.
- Constructor injection only.
- Business errors are subclasses of `BookstoreException`; wrap `SQLException` and `IOException` at the adapter boundary.
- Every public type has Javadoc; every package has a `package-info.java`.

## When implementing a feature

- Work in roadmap phase order and keep `./mvnw verify` green without a running MySQL.
- Each change comes with tests in the same module.
- If a planned class name in `docs/CURRICULUM-MAP.md` changes, or a curriculum topic moves, update that table in the same change.
- Record new architectural decisions in `docs/DECISIONS.md`.
