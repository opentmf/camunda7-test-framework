# Changelog

All notable changes to this project will be documented in this file.

## [2.1.0] - 2026-08-24

### Added

- **Chaos toolkit** (`org.opentmf.camunda.test.chaos`) — deterministic failure
  simulation against the embedded engine, all behind existing extension points
  (a Jersey filter on `/engine-rest`; disarmed it is a pass-through):
  - `EngineOutage` — makes the engine's REST surface answer `503` while the
    engine itself keeps running: exactly what an external-task worker sees when
    the engine pod dies. `AutoCloseable` (try-with-resources), with selective
    scopes (`ALL`, `FETCH_AND_LOCK`, `COMPLETION`) to cut a single leg of the
    external-task protocol.
  - `ExternalTaskProbe` — counts `fetchAndLock`/`complete`/`failure` attempts
    (refused ones INCLUDED, so "the client kept polling through the outage" is
    assertable) and exposes `queueDepth(topic)` — the number a production
    autoscaler watches.
  - `EngineChaosExtension` — JUnit 5 hygiene for tests not extending
    `BaseBpmIT`: disarms outages, clears probes, resets the clock after each
    test.
- **`EngineClock`** (`org.opentmf.camunda.test.clock`) — jumps the engine's
  `ClockUtil` forward so a `PT2H` timer is due in milliseconds. After a jump the
  clock keeps advancing, shifted by the accumulated offset (`ClockUtil.offset`),
  so later timers, retry back-offs and lock expiries still see elapsing time;
  `freezeAt(instant)` pins the clock explicitly when time must stand still.
  Forward-only by design; every clock move nudges the job executor
  (`jobWasAdded()`) — without that, an acquisition thread that computed its
  wake-up under the old clock strands hours in the future and every later async
  job silently waits it out.
- **`LockSteward`** (`org.opentmf.camunda.test.lock`) — deterministic lock
  loss: `lockAs`/`expireLock`/`stealAs` produce the "another worker holds my
  task now" rejection without sleeping past lock durations.
- **Scripted task outcomes on the task expectation builder**
  (`registerTaskExecutionListener()` only — the receive-task builder rejects
  them at compile time) — `withFailure(message)` (throws the named
  `SimulatedTaskFailure`; becomes an incident when the scripted failures
  exhaust an async continuation's retries), `withBpmnError(code)` (drives the
  model's error boundary) and `withDelay(duration)` (the declarative slow
  task). Variables and consumers registered on the same expectation still
  apply first, so a failing task can leave evidence behind.
- **`BaseBpmIT.assertNoIncidentRaised(instance, window)`** — the negative twin
  of `assertIncidentCreated`: the incident query must stay empty for the WHOLE
  window (Awaitility `during`). The load-bearing assertion of chaos tests.
- `BaseBpmIT.beforeEach` now also resets outage/probe/clock state, so chaos
  never leaks between tests.
- A `sonar` Maven profile: `mvn -Psonar clean verify` runs the full suite and
  a SonarQube analysis against a local server in one command. The JaCoCo
  `report` goal moved from its default `verify` phase to
  `post-integration-test` so the scan sees integration-test coverage.

### Changed

- `VariableUtil` is now `final` with a private constructor. It only ever held
  static helpers, so the implicit public constructor was never meaningful — but
  code that instantiated or subclassed it will no longer compile.
- `assertIncidentCreated` polls every 1s instead of every 10s — an incident
  that lands in the first second no longer costs a ten-second wait per
  assertion.
- The `maven-enforcer-plugin` now pins the build toolchain to **Java 17.x** and
  **Maven 3.9.x** instead of accepting those versions as open-ended minimums.
  On JDK 23 and newer, javac disables implicit annotation processing, so Lombok
  silently stops running and the build fails with dozens of misleading "cannot
  find symbol" errors; the enforcer now names the real cause up front.
- Updates **Spring Boot to 4.1.1**, JUnit to **6.1.3**, ArchUnit to **1.5.0**,
  GraalVM to **25.3.4.1**, JaCoCo to **0.8.15**, `maven-jar-plugin` to **3.5.1**
  and `central-publishing-maven-plugin` to **0.11.0**.
- A `maven.version.ignore` property now filters pre-release qualifiers for every
  `versions:display-*` goal, so `display-property-updates` and
  `display-plugin-updates` no longer propose milestone or beta builds without a
  command-line flag.

### Security

- The Spring Boot **4.1.1** upgrade clears **15 HIGH/CRITICAL CVEs** that reached
  consumers transitively under 2.0.2's Spring Boot 4.0.6: six in
  `tomcat-embed-core` (11.0.21 → 11.0.24, including three CRITICAL — HTTP/2
  header validation, digest-authentication bypass and an improper-authorization
  bypass), six across Jackson 2.x/3.x (2.21.2 → 2.21.5, 3.1.2 → 3.1.5, covering
  two arbitrary-code-execution advisories in `jackson-databind`), two in
  `httpcore5` (5.3.6 → 5.4.3) and one SpEL denial-of-service in
  `spring-expression` (7.0.7 → 7.0.9).

## [2.0.2] - 2026-06-01

### Changed

- Switches to the **CibSeven 2.2.0** `-4`-suffixed Spring Boot 4 starter
  artifacts (`cibseven-bpm-spring-boot-starter-rest-4` and
  `cibseven-bpm-spring-boot-starter-external-task-client-4`). These are
  the native Boot 4 line, compiled against Spring 7 /
  `cibseven-engine-spring-7` with the relocated Spring Boot 4 FQNs.
- Upgrades `camunda7-incident-logger` to **2.0.1** (depends on CibSeven 2.2.0).
- Updates Spring Boot to **4.0.6**.
- Updates `opentmf-commons` to **2.2.0**.
- Updates GraalVM JavaScript to **25.0.3**.
- Updates JUnit Jupiter to **6.1.0**.

### Removed

- The Spring Boot 3→4 compatibility shims under `org.springframework.*`
  (`JerseyAutoConfiguration`, `HibernateJpaAutoConfiguration`,
  `JerseyApplicationPath`), the bridging
  `Boot4CibSevenCompatAutoConfiguration` auto-configuration, and the
  registering `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  resource — all obsoleted by the CibSeven `-4` starters.
- The `spring-boot-hibernate` direct dependency, which only existed to
  back the Boot-3-FQN compat stub.

### Notes

- Jackson 2.x is still pulled transitively by the engine; the Jackson
  2 / Jackson 3 coexistence noted in 2.0.0 continues to apply.

## [2.0.1] - 2026-04-14

### Changed

- Add Spring Boot 4 compatibility updates for CibSeven starters: modify `Boot4CibSevenCompatAutoConfiguration` to register `CibSevenJerseyFilter` and update `JerseyApplicationPath` bean
- enforce Java 17 in `pom.xml`.

## [2.0.0]

### Changed

- Upgrades to **Spring Boot 4.0.4** (from 3.5.11), including Spring Framework 7, Jakarta EE 11, and Hibernate 7.
- Switches from Camunda 7 Community Edition starters to **CibSeven 2.1.0** starters. CibSeven is an actively maintained community fork of Camunda 7 with the same database schema and REST API. This change is transparent to consuming microservices and does not affect their runtime classpath.
- Upgrades `opentmf-commons` to **2.1.0** (Jackson 3 support).
- Upgrades `camunda7-incident-logger` to **2.0.0** (CibSeven-based).
- Replaces `JacksonUtil.getDefaultObjectMapper()` with an inline Jackson 2 `ObjectMapper` in `BaseBpmUnitTest`, decoupling from `opentmf-commons` Jackson 3 API.
- Migrates `@NonNull` annotation from `org.springframework.lang` to `org.jspecify.annotations` (Spring Framework 7 requirement).
- Java source/target remains at 17.

### Added

- Spring Boot 3-to-4 compatibility stubs for `HibernateJpaAutoConfiguration`, `JerseyAutoConfiguration`, and `JerseyApplicationPath` so that CibSeven 2.1 auto-configuration (compiled against Boot 3.5) works on Boot 4. These stubs will be removed once CibSeven ships Boot 4 native starters.
- `Boot4CibSevenCompatAutoConfiguration` auto-configuration class that bridges the relocated `JerseyApplicationPath` bean.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` to register the compatibility auto-configuration.

### Notes

- CibSeven 2.1.0 starters are compiled against Spring Boot 3.5.x. They work on Boot 4 thanks to the compatibility stubs provided by this library. Jackson 2.x (pulled transitively by the engine) coexists with Jackson 3.x on the classpath.

## [1.0.8]

### Added

- `withCount(int count)` method to builders, allowing registration of the same listener multiple times in a single call (useful for tasks in loops).
- `withExecutionConsumer(Consumer<DelegateExecution> consumer)` method to builders, providing access to workflow variables within custom logic. This allows reading variables via `execution.getVariable()` and modifying them via `execution.setVariable()`.
- Introduced `CHANGELOG.md` (this file), referenced from `README.md`.
- `release` Maven profile for source jar, javadoc jar, GPG signing, and central publishing — `mvn clean verify` now works without GPG keys or publishing credentials.

### Removed

- Deprecated `registerReceiveTaskExecutionListener()` method. Use `registerMessageCatchExecutionListener()` instead.

### Changed

- Overhauled `README.md`: fixed incorrect examples, added Builder Methods reference section, corrected method signatures, and improved documentation.
- Updates Spring Boot to 3.5.11.
- Updates Maven Surefire/Failsafe plugins to 3.5.5.
- Lowered JaCoCo branch coverage threshold from 90% to 75% (line and instruction thresholds remain at 90%). The remaining uncovered branches are defensive null checks in private methods interacting with the Camunda engine runtime.

## [1.0.7]

### Changed

- Extends `registerMessageCatchExecutionListener` to support additional BPMN element types:
  - Receive Task (already supported)
  - Message Intermediate Catch Event
  - Boundary Message Event
- Deprecates `registerReceiveTaskExecutionListener` (removed in 1.0.8).

## [1.0.6]

### Changed

- Updates Documentation
- Specifies `legacyJobRetryBehaviorEnabled=true` property in test scope

## [1.0.5]

### Changed

- Updates Spring Boot to 3.5.6
- Updates Camunda to 7.24.0
- Updates Camunda Incident Logger to 1.0.4

## [1.0.4]

### Changed

- Updates Spring Boot to 3.4.4
- Updates Camunda to 7.23.0
- Updates Camunda Incident Logger to 1.0.3
- Initial open source version

## [1.0.3] (Backward Incompatible)

### Added

- Custom Task Execution Listener for all tasks.
- `TaskExecution` interface to execute the expectation of all tasks.
- New methods to the `CamundaExpectationUtil` class.
- New class `TaskExecutionRegistry` to manage the expectations of all tasks.

### Changed

- Updates Spring Boot to 3.4.2

## [1.0.2]

### Changed

- Updates Spring Boot to 3.4.0
- Updates Camunda Incident Logger to 1.0.2

## [1.0.1]

### Changed

- Updates Camunda to 7.22.0 together with related libraries.

## [1.0.0]

### Added

- Initial Version

[2.0.2]: https://github.com/opentmf/camunda7-test-framework/compare/v2.0.1...v2.0.2
[2.0.1]: https://github.com/opentmf/camunda7-test-framework/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.8...v2.0.0
[1.0.8]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/opentmf/camunda7-test-framework/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/opentmf/camunda7-test-framework/releases/tag/v1.0.0
