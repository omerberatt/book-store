# Book Store

Online book store built as the "Lab project" of the **Java Patterns and Practices** course. The curriculum is the course notes at https://github.com/software-lecture-labs/java-study-notes (chapters 01–14, ADRs in `docs/adr`). When a design question comes up, check how the notes handle it first and follow them unless an ADR here says otherwise.

Read before non-trivial work: `docs/SPEC.md` (what), `docs/ARCHITECTURE.md` (target architecture), `docs/adr/` (decisions), `docs/CURRICULUM-MAP.md` (which chapter goes where), `docs/ROADMAP.md` (phase order).

## Current state vs target

Phase 4.1 is done: the code lives in the business-area modules below, with a technical `shared-jdbc` module for the JDBC toolkit (ADR-0018), and modules use each other only through their `api` packages (`catalog.api.StockFacade`/`RatingFacade`, `customer.api.CustomerDirectory`, `sales.api.PurchaseHistory`). Still to come (`docs/ROADMAP.md`): the remaining ArchUnit rules (4.2), typed ids, `DomainException` and `Specification` (4.3), the `api` contract for the web layer and a composition root (4.4), the event publisher (4.5), PostgreSQL and contract tests (4.6). There is no web layer yet; tests run on H2 in MySQL mode. `docs/ARCHITECTURE.md` section 9 lists every remaining difference.

Each module's migrations live in `src/main/resources/db/migration/<module>` with their own Flyway history table; `ModuleMigrations.ALL` gives the order (catalog, customer, sales, review). Each module shares its test fixtures as a `test-jar` (`CatalogDb`, `CustomerDb`, `SalesDb`, in-memory repositories, `TestDatabase`, `TestClock`).

## Commands

- Build and test everything: `./mvnw -B -ntp verify` (Git Bash) or `mvnw.cmd -B -ntp verify` (cmd). Must pass without Docker.
- One module with its dependencies: `./mvnw -B -ntp -pl <module> -am verify`
- Requires JDK 21. On this machine: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`. Maven comes from the wrapper.

## Workflow (ADR-0017)

- Work directly on `main`: commit and push to `main`. Do not create `develop`, `feature/*`, `release/*` or `hotfix/*` branches and do not open Pull Requests unless the user asks. (ADR-0001 adopted Gitflow; ADR-0017 replaced it at the owner's request.)
- Every commit pushed to `main` passes `./mvnw verify`. Keep each phase or phase step in its own commit. If a release is made, tag `main` with CalVer: `v2026.11.1`.
- Commit messages in English, imperative, one topic each.
- A change that makes or changes an architectural decision adds or amends an ADR in `docs/adr/` in the same commit. Accepted ADRs are never deleted; superseded points get a dated note.
- If a curriculum topic moves or a planned class name changes, update `docs/CURRICULUM-MAP.md` in the same change.

## Target architecture

- Modules by business area (ADR-0002): `shared-kernel`, `catalog`, `customer`, `sales`, `review`, `app` (WAR), `quality/architecture-tests`, plus the technical `shared-jdbc` (ADR-0018). No module is a layer.
- Inside each business module, packages `api`, `application`, `domain`, `persistence`, `rest`. Allowed directions are the matrix in ADR-0003: domain sees only shared-kernel; api is a pure contract; application sees domain and api; persistence sees domain; rest sees api. A business module uses another module only through its `api` package. No cycles.
- Platform (ADR-0010): Jakarta EE 11 Web Profile + MicroProfile 6.1 on Payara Micro 7. CDI with `annotated` discovery and constructor injection; JTA `@Transactional` on use cases; CDI events observed `AFTER_SUCCESS`; JAX-RS + JSON-B; MicroProfile Config and OpenAPI. Until Phase 6, wiring is a hand-written composition root and transactions go through the `TransactionRunner` port.
- Database (ADR-0008): PostgreSQL 17. Fast tests on H2 in PostgreSQL mode; the same contract tests run on PostgreSQL via Testcontainers when Docker is available and are skipped otherwise. Hand-written JDBC with `PreparedStatement`; no JPA, Spring Data or `JdbcTemplate` (JPA is a Phase 9 option). Each module owns its Flyway migrations under `db/migration/<module>`. Never edit an applied migration. No `IN (...)` lists in `CHECK` constraints (H2 2.4 bug); use reference tables.
- HTTP (ADR-0009): `/api/v1`, request records with Bean Validation for format only, RFC 9457 Problem Details, status from `DomainException.Kind` (NOT_FOUND 404, CONFLICT 409, RULE_VIOLATION 422, FORBIDDEN 403; format errors 400; unexpected 500 without internals).
- UI (ADR-0011): plain HTML, CSS and JavaScript in `app/src/main/webapp` calling the REST API with `fetch`. No build step, no JS framework.

## Code rules (ADR-0004, ADR-0005, ADR-0006)

- Domain and shared-kernel depend on the JDK only: no `jakarta..`, `javax..`, `java.sql..`, no framework annotations.
- No setters in the domain; state changes through intention-revealing methods.
- `Optional` only as a return type, never as a field or record component.
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
- Each repository interface has one contract test that the in-memory, H2 and PostgreSQL implementations all pass. In-memory repositories store and return copies.
- Architecture rules live in `quality/architecture-tests` as ArchUnit tests and are named after the ADRs that require them.
