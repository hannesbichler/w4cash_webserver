# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot REST API (Java 21) that wraps the Oracle database of **w4cash**, a point-of-sale system
forked from Openbravo POS. `rest/` is a Maven multi-module sub-project (root `pom.xml` just declares the
`rest` module and shared dependency versions). The app talks directly to the existing w4cash Oracle schema
(PRODUCTS, PEOPLE, PLACES, CATEGORIES, etc.) over plain JDBC — it is not creating its own data model.

The project scaffold originated from the Spring "Building REST Services with Spring" tutorial
(`tut-rest`, see `README.adoc`), which explains some vestigial pieces described below.

## Build & run

```
./mvnw -pl rest compile -q      # compile
./mvnw -pl rest spring-boot:run # run the server (listens on 0.0.0.0:3000, see application.properties)
```

Run/debug via VS Code is also configured (`.vscode/launch.json`, main class `w4cash.W4cashApplication`).

## Tests

```
./mvnw -pl rest test                              # full suite (performance tests excluded by default)
./mvnw -pl rest test -Dtest=ProductsControllerTest # single test class
./mvnw -pl rest test -Pperf                        # run only @Tag("performance") tests
```

Controller tests use `@WebMvcTest` + `@MockBean` on the repository (see `ProductsControllerTest`) — they
do not hit a real database. There is no repository-layer test database configured for the raw-JDBC
repositories; repository tests that do exist (e.g. `ProductsRepositoryTest`) run against the real Oracle
connection via `LoadDatabase`.

## Architecture

### Database access — two coexisting patterns

- **Current pattern (use this for new work):** a HikariCP pool against the real w4cash Oracle schema,
  configured in `DataSourceConfig#w4cashDataSource` from the legacy `AppConfig` credentials (see below).
  Feature repositories (e.g. `product/ProductsRepository`, `attribute/AttributeSetsRepository`)
  hand-write SQL with `PreparedStatement`/`ResultSet`, no Spring Data / JPA involved. Borrow a
  connection per unit of work and **always** close it via try-with-resources:

  ```java
  try (Connection conn = LoadDatabase.getConnection();
          PreparedStatement st = conn.prepareStatement(SQL)) { ... }
  ```

  Anything that must be atomic — or that takes `LOCK TABLE` — has to hold **one** connection for its
  whole duration and pass it down to helpers (see `PaymentController#postPayment`,
  `KassenabschlussController#zReport`). Do not open a second connection mid-transaction; it is a
  separate Oracle session and will not see the uncommitted work or share the lock.
- **JPA/H2 (only chat; don't extend):** the in-memory H2 datasource now backs exactly one working
  feature — `chat/ChatMessage` + `chat/ChatRepository`. Note this means **chat history is discarded on
  every restart** (`ddl-auto=create-drop`, in-memory). `receipt/ActiveCash` and `settings/Settings` are
  still `@Entity` and their repositories are still injected into their controllers, but nothing calls
  them — dead weight, safe to delete.

  A "mirror" pattern used to exist here and is gone: `persons`, `places`, `categories`, `floors` and
  `TicketInfos` each wiped a JPA table (`deleteAll()`) and repopulated it from Oracle on **every GET**,
  then returned the H2 rows. Concurrent requests interleaved those wipes, so callers saw partial or
  duplicated lists — and because `/places` and `/places/{floorId}` shared one table, a concurrent call
  could return places from the wrong floor. These controllers now stream Oracle rows straight into the
  response; do not reintroduce a mirror. The entities are plain DTOs, and the real Oracle key is
  `id_` (the generated JPA `Long id` is gone from the JSON).

### Startup / configuration

`W4cashApplication` builds a static `AppConfig` (`com.openbravo.pos.forms.AppConfig`, from the legacy
`w4cash.jar`) and calls `.load()` *before* Spring context startup — this is where `db.driver`, `db.URL`,
`db.user`, `db.password` (optionally `crypt:`-prefixed and decrypted with `AltEncrypter`) come from. These
are **not** read from `spring.datasource.*`; `DataSourceConfig#w4cashDataSource` pulls them off `AppConfig`
to build the Oracle pool.

`DataSourceConfig` declares **two** datasources, and both must stay declared: Boot's
`DataSourceAutoConfiguration` is `@ConditionalOnMissingBean(DataSource.class)`, so the moment the Oracle
pool exists the auto-configured H2 that JPA binds to disappears. `jpaDataSource` (H2, in-memory) is
therefore explicit and `@Primary` so Hibernate keeps binding to H2; `w4cashDataSource` is the Oracle pool
reached through `LoadDatabase.getConnection()`. Pool size and connection timeout are tunable via
`w4cash.datasource.*` in `application.properties`.

The Oracle pool sets `initializationFailTimeout=-1`, so the app still starts when the database is
unreachable (endpoints then fail per-request), matching the old behaviour — but unlike the previous single
long-lived connection, the pool revalidates and replaces broken connections, so a dropped session no
longer requires a restart.

### The `w4cash.jar` dependency and the top-level `com/` tree

`rest/pom.xml` depends on `lib/w4cash.jar` (Openbravo POS core, incl. `com.openbravo.pos.forms.AppConfig`,
`com.openbravo.pos.util.AltEncrypter`, the ticket/printer scripting engine, etc.) as a `system`-scoped jar.
The `com/openbravo/...` source tree at the repo root (e.g. `ScriptEngineVelocity.java`) is **reference
source for that jar's classes**, kept for lookup — it is not on any Maven source root and is not compiled
as part of the build.

### Per-feature package convention

Each domain area lives under `w4cash.<feature>` with `Controller` + `Repository` (+ a `NotFoundException`
where the legacy JPA-throwing pattern is still used) — e.g. `category`, `floors`, `place`, `taxcategory`,
`attribute`. Controllers in the current (non-legacy) style catch `SQLException` per-endpoint and translate
it to a 500 with a message, rather than relying on `@ControllerAdvice`.

Note: `settings/TicketInfoRepository` and `ticketinfo/TicketInfoRepository` are two distinct classes with
the same simple name in different packages — check the package when navigating to one.

### Printing subsystem (`print/`, `printer/`)

`TicketPrintService` renders kitchen/bar tickets and receipts two ways: ESC/POS byte sequences sent
directly to a `javax.print.PrintService` for real thermal printers, and a `Printable`-based fallback
(custom pagination/wrapping) when the target is a PDF printer. Printer names/indexes-per-category come
from `AppConfig` machine properties (`machine.printer`, `machine.printer.2`, ...) and the `CATEGORIES.PRINTER`
column. Payment/full receipts (`printPayment`) still go through the legacy Velocity-templated
`ScriptEngine` path inherited from Openbravo POS. Every print attempt is recorded via
`PrintJobRepository` (success/failure + raw content) to support `reprint`.

### Auth (`auth/`)

`OtpController`/`OtpService` implement a simple phone-number OTP flow keyed off `PEOPLE.CARD`. OTPs are
generated in-memory (`ConcurrentHashMap`, no expiry) and currently only logged, not sent via SMS — see the
`TODO` in `OtpService.generate`.

### CORS

`WebConfig` allows all origins/methods/headers on `/**` — intentionally permissive for this internal API.
