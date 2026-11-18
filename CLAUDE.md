# Book Store

Online book store built as the "Lab project" of the **Java Patterns and Practices** course. The curriculum is the course notes at https://github.com/software-lecture-labs/java-study-notes (chapters 01–14, ADRs in `docs/adr`). When a design question comes up, check how the notes handle it first and follow them unless an ADR here says otherwise.

Read before non-trivial work: `docs/SPEC.md` (what), `docs/ARCHITECTURE.md` (target architecture), `docs/adr/` (decisions), `docs/CURRICULUM-MAP.md` (which chapter goes where), `docs/ROADMAP.md` (phase order).

## Current state vs target

Phases 4 and 5 are done: the code lives in the business-area modules below, with a technical `shared-jdbc` module for the JDBC toolkit (ADR-0018), and modules use each other only through their `api` packages (`catalog.api.StockFacade`/`RatingFacade`, `customer.api.CustomerDirectory`, `sales.api.PurchaseHistory`). The rules of ADR-0003 to ADR-0008 are ArchUnit tests in `quality/architecture-tests` (one class per ADR). Aggregates have typed ids (`Entity<I extends Identifier>`; `BookId` and `CustomerId` in the shared kernel), errors are `DomainException`s with a `Kind`, and the catalog search is a `Specification<Book>` that `BookSearchSql` translates. Each module offers façades in its `api` package (shared-kernel ids typed, module-private ids as `long`, enums as codes; mappers in `application`), and `app` has the hand-written composition root `BookstoreModule`. Use cases publish events after saving through the `DomainEventPublisher` port (`InProcessEventPublisher`; `OrderNotificationObserver` feeds the `Notifier` adapter). Migrations are written for PostgreSQL 17 with reference tables for code columns; tests run on H2 in PostgreSQL mode, and each module's repository contract (`…RepositoryContract`) also runs in memory and on PostgreSQL via Testcontainers (skipped without Docker); `docker-compose.yml` starts a local PostgreSQL. Phase 5 gave each module a `rest` package. Phase 6 is done: the app became a WAR on Payara Micro 7: CDI beans (`@ApplicationScoped`, `@Inject` constructors, `beans.xml` annotated), `BookstoreWiring`/`BookstoreDataSource` producers, `JtaTransactionRunner` for the `TransactionRunner` port, CDI events observed `AFTER_SUCCESS`, `BookstoreStartup` migrations, and JAX-RS resources with JSON-B and the `shared-web` providers (problem mappers, `BasicAuthenticationFilter`, `JsonbConfiguration`). HTTP tests run the resources on Jersey's JDK HTTP container (`RestTestServer`); `CdiWiringTest` checks the wiring in Weld SE. `BookstoreModule` still wires everything by hand for the H2 tests. MicroProfile OpenAPI serves the API description at `/openapi`. Phase 7 is done (ADR-0020): passwords are BCrypt (`BcryptPasswordHasher`, old PBKDF2 hashes still verify) and Jakarta Security signs callers in (`app.security`: `BookstoreAuthenticationMechanism` for HTTP Basic and the `POST/GET/DELETE /api/v1/session` cookie session, `AccountIdentityStore` with the customer id as caller name and the role as group, `ClientHeaderFilter` requiring `X-Bookstore-Client` on state-changing cookie requests). `Callers` reads any principal that way; `BasicAuthenticationFilter`/`Authenticator` (shared-web) and `AccountAuthenticator` (customer) are test-only stand-ins used by `RestTestServer`. A visitor's cart lives in the HTTP session (`sales.rest.SessionGuestCart`, `@SessionScoped`, behind `GuestCartHolder`) and joins the stored cart at sign-in. The customer pages live in `app/src/main/webapp` (ES modules, no build step: `js/api.js` sends the client header and turns problems into `ApiError`, `js/i18n.js` loads `i18n/tr.json` or `en.json` and picks error texts by problem `code`, `js/layout.js`, `js/dom.js` builds nodes without `innerHTML`, one `js/pages/<page>.js` per page); `WebPagesTest` keeps the dictionaries, pages and error codes in step. Phase 8 (ADR-0021, admin panel) is under way: 8.1 added `catalog.api.CatalogAdminFacade` (`CatalogAdminService`) with `/api/v1/admin/books|authors|categories` and `/admin/books/stats`; 8.2 added cover images (`catalog.domain.FileStorage` port, `FileSystemFileStorage` in a directory from `bookstore.files.directory` via `BookstoreFiles`, `PUT /admin/books/{id}/cover` with the raw image, `GET /books/{id}/cover` with an ETag). 8.3 added `customer.api.CustomerAdminFacade` (`/admin/customers`; administrators cannot be switched off; the mechanism ends the session of a switched-off account via `AccountFacade.isActive`) and `review.api.ReviewAdminFacade` (`/admin/reviews`; removal also takes the rating out). Next are the admin pages (8.4). `docs/ARCHITECTURE.md` section 9 lists every remaining difference.

Each module's migrations live in `src/main/resources/db/migration/<module>` with their own Flyway history table; `ModuleMigrations.ALL` gives the order (catalog, customer, sales, review). Each module shares its test fixtures as a `test-jar` (`CatalogDb`, `CustomerDb`, `SalesDb`, in-memory repositories, `TestDatabase`, `TestClock`).

## Commands

- Build and test everything: `./mvnw -B -ntp verify` (Git Bash) or `mvnw.cmd -B -ntp verify` (cmd). Must pass without Docker.
- One module with its dependencies: `./mvnw -B -ntp -pl <module> -am verify`
- Run the API on Payara Micro: `./mvnw -B -ntp install -DskipTests` then `./mvnw -B -ntp -pl app -Ppayara package exec:exec` (http://localhost:8080/api/v1; in-memory H2 with demo accounts `ayse@bookstore.local` and `admin@bookstore.local`, password `kitap2026`, unless `BOOKSTORE_DB_*` say otherwise). From Git Bash never pass `--contextRoot /` to java yourself: MSYS turns `/` into a Windows path. Payara must run with `-Duser.language=en` (the profile adds it): on a Turkish default locale it fails to start (`DYNAMİC`).
- Requires JDK 21. On this machine: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`. Maven comes from the wrapper.

## Workflow (ADR-0017)

- Work directly on `main`: commit and push to `main`. Do not create `develop`, `feature/*`, `release/*` or `hotfix/*` branches and do not open Pull Requests unless the user asks. (ADR-0001 adopted Gitflow; ADR-0017 replaced it at the owner's request.)
- Every commit pushed to `main` passes `./mvnw verify`. Keep each phase or phase step in its own commit. If a release is made, tag `main` with CalVer: `v2026.11.1`.
- Commit messages in English, imperative, one topic each.
- A change that makes or changes an architectural decision adds or amends an ADR in `docs/adr/` in the same commit. Accepted ADRs are never deleted; superseded points get a dated note.
- If a curriculum topic moves or a planned class name changes, update `docs/CURRICULUM-MAP.md` in the same change.

## Target architecture

- Modules by business area (ADR-0002): `shared-kernel`, `catalog`, `customer`, `sales`, `review`, `app` (WAR), `quality/architecture-tests`, plus the technical `shared-jdbc` (ADR-0018) and `shared-web` (ADR-0019, the hand-written HTTP boundary used by the `rest` packages until Phase 6). No module is a layer.
- Inside each business module, packages `api`, `application`, `domain`, `persistence`, `rest`. Allowed directions are the matrix in ADR-0003: domain sees only shared-kernel; api is a pure contract; application sees domain and api; persistence sees domain; rest sees api. A business module uses another module only through its `api` package. No cycles.
- Platform (ADR-0010): Jakarta EE 11 Web Profile + MicroProfile 6.1 on Payara Micro 7. CDI with `annotated` discovery and constructor injection; JTA `@Transactional` on use cases; CDI events observed `AFTER_SUCCESS`; JAX-RS + JSON-B; MicroProfile Config and OpenAPI. Until Phase 6, wiring is a hand-written composition root and transactions go through the `TransactionRunner` port.
- Database (ADR-0008): PostgreSQL 17. Fast tests on H2 in PostgreSQL mode; the same contract tests run on PostgreSQL via Testcontainers when Docker is available and are skipped otherwise. Hand-written JDBC with `PreparedStatement`; no JPA, Spring Data or `JdbcTemplate` (JPA is a Phase 9 option). Each module owns its Flyway migrations under `db/migration/<module>`. Never edit an applied migration. No `IN (...)` lists in `CHECK` constraints (H2 2.4 bug); use reference tables.
- HTTP (ADR-0009): `/api/v1`, request records with Bean Validation for format only, RFC 9457 Problem Details, status from `DomainException.Kind` (NOT_FOUND 404, CONFLICT 409, RULE_VIOLATION 422, FORBIDDEN 403; format errors 400; unexpected 500 without internals).
- UI (ADR-0011): plain HTML, CSS and JavaScript in `app/src/main/webapp` calling the REST API with `fetch`. No build step, no JS framework.

## Code rules (ADR-0004, ADR-0005, ADR-0006)

- Domain and shared-kernel depend on the JDK only: no `jakarta..`, `javax..`, `java.sql..`, no framework annotations.
- No setters in the domain; state changes through intention-revealing methods.
- `Optional` only as a return type, never as a field, record component or parameter. Keep a nullable field and offer an `Optional` accessor (a final class), or use a nullable record component in read models and requests.
- Value objects are `record`s validated in the constructor. Aggregates use typed ids (`BookId`, `OrderId`, …) and refer to other aggregates by id only. Stored aggregates are rebuilt with `reconstitute(...)`, called only from persistence.
- Domain exceptions extend `DomainException` and carry a `Kind` and a stable `code`.
- Money is always `Money` (`BigDecimal`), never `double`. Time comes from an injected `Clock`; never call `Instant.now()`, `LocalDate.now()` or `System.currentTimeMillis()` directly.
- Application classes take collaborators through the constructor into `private final` fields and know only interfaces, never `Jdbc…` or `InMemory…` classes.
- Aggregates raise events; only application services pull and publish them, after saving.
- No `System.out`, `System.err` or `printStackTrace()` in business modules; use `System.Logger`. No packages named `util`, `common`, `helper` or `misc`.
- Code, identifiers, table names and commit messages in English; documents under `docs/` and the README in Turkish.
- Every public type has Javadoc; every package has a `package-info.java`.

## Tests

- Every change comes with tests in the same module. Keep `./mvnw verify` green without Docker.
- Each module has one abstract repository contract (`CatalogRepositoryContract` …) with `InMemory…Test`, `H2…Test` and `Postgres…Test` (`@EnabledIfDocker`) subclasses; raw-SQL constraint tests live in `…SchemaConstraintsTest` (H2 and PostgreSQL). Fixtures (`CatalogDb`, `CustomerDb`, `SalesDb`, `ReviewDb`) build any of the three. In-memory repositories store and return copies and reject stale versions.
- Architecture rules live in `quality/architecture-tests` as ArchUnit tests and are named after the ADRs that require them.
