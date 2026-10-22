# DEVLOG

## Day 1: Mon Oct 19 – Tue Oct 20
**Goal:** Spring Boot app connected to PostgreSQL in Docker, `/actuator/health` = UP.

**Done**
- Generated the project at start.spring.io (Kotlin, Gradle Kotlin DSL, Spring Boot 4.1.1, Java 21, Web, Data JPA, PostgreSQL Driver, Actuator).
- Ran Postgres 18 in a Docker container with a named volume and a port mapping.
- Connected the app through `spring.datasource.*` in `application.properties`.

**What broke and how I fixed it**
- Code Runner tried to compile the file with `kotlinc`. Spring Boot apps are run through Gradle (`gradlew.bat bootRun`), so I stopped using Code Runner.
- "Failed to configure a DataSource": the properties weren't saved in VS Code. Turned on Auto Save.
- Postgres 18 moved its data directory, so the volume has to be mounted at `/var/lib/postgresql`, not `/var/lib/postgresql/data`.

**Tested failure:** stopped the Postgres container while the app ran. Health went DOWN, then recovered to UP after restarting it.
**Learned:** I ran Postgres as a container from the `postgres:18` image. The data lives in a named volume, so it survives deleting the container. Actuator's health check includes the database, so stopping the container turned it DOWN.

## Day 2: Oct 20 — Data model and Target entity

**Did:** Sketched Target and CheckResult. Wrote the Target entity and TargetRepository.
Hibernate created the `targets` table on startup; confirmed it in psql and tested
every constraint with raw SQL.

**Decisions:**
- Missing values are NULL, never fake values like 0 or -1
  (a timeout has no status code, and fake values corrupt averages).
- `up` is a separate boolean so uptime % is a simple count.
- Targets can be paused (active = false, history kept) or deleted (cascades to check results).
- `url` is unique, enforced by the database.

**Broke:**
- `.\gradlew.bat` failed in Git Bash: Bash treats `\` as an escape character. Use `./gradlew`.
- Project was nested one folder too deep; flattened it.
- "wrong name" error on bootRun: package lines still said `MalekBaghdadi` while folders
  were lowercase. Java packages are case-sensitive, Windows folders aren't.
  Fixed with case-sensitive search, two-step `git mv`, and `gradlew clean`.
- "no password was provided": forgot to run with the local profile
  (`--args="--spring.profiles.active=local"`).

**Learned:**
- Kotlin default values don't exist in the database.
- IDs are unique, not consecutive: failed inserts still use up a number.

**Notes for later:**
- varchar(255) may be too short for URLs (Thursday).
- Give the unique constraint a readable name (Thursday).
- App should use a non-superuser database user on the server.
## Day 3: Oct 21 — Target REST API

**Goal:** [your 2–3 sentences from Step 1]

**Did:** Built TargetController with POST /targets, GET /targets, GET /targets/{id},
DELETE /targets/{id}, and POST /targets/{id}/pause and /resume. Tested every endpoint
with curl.

**Decisions:**
- The API accepts a CreateTargetRequest DTO, not the entity, so clients can't set
  id or active.
- Pause/resume are action endpoints (POST /{id}/pause). Alternative: PATCH /{id}
  with {"active": false}.
- No service layer yet: the controller calls the repository directly. A service
  comes Friday, when there's real logic.
- Returning the entity as the response for now; a response DTO may come later.

**Broke (on purpose):**
| Test          | Got | Should be  |
|---------------|-----|------------|
| Duplicate URL | 500 | 409        |
| Missing url   | 400 | 400, but message is vague |
| GET /999      | 500 | 404 (pause/resume too) |
| DELETE /999   | 204 | 204 or 404, to decide |
| GET /abc      | 400 | 400        |
| PUT /targets  | 405 | 405, with Allow header |

**Learned:**
- Spring maps some exceptions to 4xx itself; anything it doesn't recognize becomes 500.
- deleteById silently ignores missing IDs (Spring Data 3+).
- DELETE is idempotent: repeating it leaves the same state.
- Spring Boot hides exception details in error responses by default, to avoid leaking internals.
- Kotlin non-null types reject missing JSON fields for free.

**Notes for Thursday:**
- 409 for duplicate URL, 404 for missing IDs, decide on DELETE /999.
- Clear error messages without leaking internals.
- Leftovers from Day 2: varchar(255) for URLs, readable name for the unique constraint.
cat >> DEVLOG.md << 'EOF'

## Day 4: Oct 21 — Validation and error responses

**Goal:** Make the API reject bad input with clear 4xx responses instead of 500s,
so a client always knows what it did wrong, without the server leaking internals.

**Did:** Added Bean Validation to CreateTargetRequest (name, url, interval rules).
Added GlobalExceptionHandler extending ResponseEntityExceptionHandler, so every error
uses RFC 9457 Problem Details. Added NotFoundException (404) and ConflictException (409).
Widened url to varchar(2048), name to varchar(100), renamed the unique constraint
to uk_targets_url.

**Scorecard:**
| Test                    | Day 3       | Day 4                          |
|-------------------------|-------------|--------------------------------|
| Duplicate URL           | 500         | 409                            |
| Missing url             | 400, vague  | 400 with field errors          |
| GET/pause/resume /999   | 500         | 404                            |
| DELETE /999             | 204         | 404                            |
| GET /abc                | 400         | 400, Problem Details format    |
| PUT /targets            | 405         | 405, Problem Details + Allow    |
| ftp:// URL, blank name  | accepted    | 400 with field errors          |
| URL of 300 chars        | too long    | 201 (limit now 2048)           |

**Decisions:**
- DELETE on a missing ID returns 404, consistent with GET/pause/resume.
  Still idempotent: idempotency is about server state, not identical responses.
- Duplicates checked twice: existsByUrl for a clear message, plus the DB unique
  constraint as a safety net against race conditions (two requests at once).
- Validation errors are 400 (Spring's default). Alternative: 422 for invalid values.
- Interval limited to 30-3600 seconds.
- URL check is shape only (http/https), not reachability.
- Keep @field: on annotations even though the compiler flag makes it optional.
- One generic NotFoundException in common/, reusable for CheckResult.

**Broke:**
- Missing url gave a vague 400: Kotlin's non-null String made Jackson fail before
  validation ran. Fixed by defaulting name and url to "", so @NotBlank catches them.
- Build failed with "Unresolved reference" x4 and "Expecting a top level declaration":
  an extra } closed the class early. The odd-one-out error was the real clue.
- Safety net test: disabled existsByUrl, duplicate reached Postgres, handler
  returned a generic 409 and logged the constraint details server-side.

**Learned:**
- Request layers: Jackson -> validation -> controller -> repository -> DB.
- ResponseEntityExceptionHandler converts all Spring MVC errors to Problem Details.
- Tomcat sends Connection: close after a 400 (it can't trust the rest of the stream).
- ddl-auto=update never changes or renames existing columns; schema changes
  needed manual ALTER TABLE. Migration tools (Flyway) exist for this.
- -Xannotation-default-target=param-property and -Xjsr305=strict in build.gradle.kts.
- Validation and DB limits must agree, or the DB throws after validation passes.

**Known limitations:**
- https://github.com and https://github.com/ count as different URLs
  (URL normalization: maybe in the buffer week).
- Explicit "url": null gives a generic "Failed to read request".

**Notes for Friday:**
- CheckResult entity; deleting a target must cascade to its results.
- Service layer arrives with the check logic.
- Target 12 points at a page that doesn't exist: useful as a broken target.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 5: Oct 21 — First real check of a URL

**Goal:** Make the app actually check a website: request its URL, time the answer,
and save the result, so there's real data to build uptime numbers on.

**Did:** Added the CheckResult entity (many results per target) and its repository.
Added CheckService, the first service layer, which requests a URL with Java's
HttpClient and saves the result. Added a temporary POST /targets/{id}/check endpoint
and a CheckResultResponse DTO.

**Decisions:**
- Up = status 200-399, after following redirects.
- GET request, not HEAD (some servers handle HEAD badly).
- Timeouts: 5 s to connect, 10 s for the response.
- Response time = until the status code arrives, not the whole page.
- Deleting a target deletes its results in the database (ON DELETE CASCADE via
  @OnDelete), in one statement instead of loading and deleting each row.
- The endpoint returns a DTO, not the entity (the lazy Target doesn't convert to JSON well).
- Manual check works on paused targets; the scheduler will skip them.
- No index on (target_id, checked_at) yet, on purpose: Week 6's load test should find it.

**Broke:**
- First check of GitHub took 32 s. curl showed the network was slow too
  (14 s to first byte, 21 s total). The check was also downloading the whole page.
  Switched to stopping the clock when the status code arrives: 535 ms.
- \d check_results said "not found": I checked before the app had finished starting.

**Tests:**
| Test                    | Result                                     |
|-------------------------|--------------------------------------------|
| GitHub                  | up, 200, ~0.5 s                            |
| Page that doesn't exist | down, 404                                  |
| Target 999              | 404 from our API                           |
| Domain .invalid         | down, no status, ConnectException          |
| Closed port 9999        | down, no status, ConnectException          |
| Delete target           | its 2 results deleted too                  |

**Learned:**
- @ManyToOne + @JoinColumn creates the foreign key column (target_id).
- JPA cascade = REMOVE loads every child row; @OnDelete lets Postgres do it in one go.
- curl -w can split time into DNS, connect, first byte and total.
- A response time that includes downloading the page measures the network, not the site.

**Known limitations / leftovers:**
- DNS failure and connection refused both show "ConnectException: no details";
  the real reason is inside the exception's cause (fix on Oct 27).
- The foreign key has a random name (fkjc1s...).

**Next:** Week 1 checkpoint recording, then Monday: @Scheduled checks every 60 s,
remove TempCheckController.
EOF


cat >> DEVLOG.md << 'EOF'

## Day 6: Oct 22 (plan: Mon Oct 26) — Scheduled checks

**Goal:** Today the app starts checking targets by itself. A scheduled job runs every 60 seconds,
checks every active target, and saves a result for each, so I no longer need to call an endpoint.
Paused targets are skipped, and the temporary check endpoint is removed.

**Did:** Turned on scheduling, added CheckScheduler and findByActiveTrue(), removed
TempCheckController. Added a User-Agent header to every check.

**Decisions:**
- fixedDelay (60 s after the last run finishes), not fixedRate, so runs never overlap.
- Every active target is checked each minute; intervalSeconds is ignored for now.
  Honoring it needs "when was this target last checked?", which comes Wed Oct 28.
- try/catch around each target, not the whole loop: one crash can't skip the others.
- One summary log line per run: count, time, up / down / crashed.

**Broke:**
- Wikipedia came back 403. Java's default User-Agent (Java-http-client/21) is blocked.
  curl proved it: our User-Agent got 200, Java's got 403. Fixed by sending
  "UptimeMonitor/0.1 (+repo link)".

**Tests:**
| Test                          | Result                                  |
|-------------------------------|-----------------------------------------|
| Normal run (4–5 targets)      | under 1 s                               |
| Paused target 12              | no new rows while paused                |
| Slow site (httpbin delay 8 s) | run went from 676 ms to 10,293 ms       |
| Wikipedia                     | 403 → 200 after the User-Agent fix      |

**Learned:**
- @Scheduled does nothing without @EnableScheduling.
- Spring's scheduler has one thread (scheduling-1), so checks run one after another.
- findByActiveTrue() becomes WHERE active = true (visible in the Hibernate log).
- fixedRate vs fixedDelay vs cron.
- A site can be up for a browser and "down" for a bot, based only on the User-Agent.
- Postgres shows timestamps in UTC (22:12 = 01:12 Beirut).

**Known limitations:**
- intervalSeconds ignored (Wed Oct 28).
- Sequential checks: one slow site delays all the others (Week 6: parallel checks).
- A site taking ~10 s flips between up and down because of the 10 s timeout (Tue Oct 27).
- Week 1 checkpoint video not recorded yet.

**Next:** Tue Oct 27: handle timeouts, connection refused, DNS and SSL errors properly,
and fix "ConnectException: no details".
EOF

cat >> DEVLOG.md << 'EOF'

## Day 7: Oct 22 — Failure handling

**Goal:** [your 2–3 sentences]

**Did:** Added an ErrorType enum and an error_type column. CheckService now walks the
exception's cause chain and labels each failure: DNS, CONNECTION_REFUSED, CONNECT_TIMEOUT,
REQUEST_TIMEOUT, SSL, HTTP_STATUS, INVALID_URL, OTHER. Added test targets 18–22.

**Decisions:**
- A separate error_type column, not just better messages: the type is for queries and
  alerts, the message is for people.
- EnumType.STRING, not the position number, so reordering the enum can't corrupt old rows.
- Classify most specific first (a connect timeout is also a timeout; DNS failures arrive
  wrapped in a ConnectException).
- Save only the outer error and the root cause: the middle ones repeat, and take(500)
  would cut the root cause, which is the useful part.
- 4xx/5xx answers are labeled HTTP_STATUS.

**Broke:**
- DNS failure and closed port both showed "ConnectException: no details". The real
  reason was two levels down: UnresolvedAddressException vs ClosedChannelException.
- The expired-certificate message repeated 3 times; the expiry date was only at the end.
- 10.255.255.1 is labeled REQUEST_TIMEOUT, not CONNECT_TIMEOUT. The gap between
  checked_at values showed each check took 10 s, so the 5 s connect timeout didn't fire
  (it did once). Cause unknown.

**Tests:**
| Target                     | Failure            | Label              |
|----------------------------|--------------------|--------------------|
| 18 nothing-here.invalid    | DNS                | DNS                |
| 14 localhost:9999          | closed port        | CONNECTION_REFUSED |
| 19 10.255.255.1            | no answer          | REQUEST_TIMEOUT (expected CONNECT_TIMEOUT) |
| 20 httpbin delay/15        | slow reply         | REQUEST_TIMEOUT    |
| 21 expired.badssl.com      | expired cert       | SSL                |
| 22 wrong.host.badssl.com   | cert for wrong host| SSL                |
| 12 missing page            | 404                | HTTP_STATUS        |

**Learned:**
- Exceptions wrap other exceptions (e.cause). The real reason is often at the bottom.
- HttpConnectTimeoutException is a kind of HttpTimeoutException, and
  UnresolvedAddressException is a kind of IllegalArgumentException, so order matters.
- Hibernate's auto-update added the column plus a CHECK rule listing the enum values.
  It won't update that rule if I add a value later: a reason to use Flyway.
- Gaps between checked_at values show how long each check took.
- Expired certificate vs hostname mismatch: two different SSL failures (Network+).

**Known limitations:**
- Connect timeout not reliable (target 19).
- Rows from before today have no error_type.
- The broken test targets add about 25 s to every run, since checks are sequential.

**Next:** Wed Oct 28: latest status endpoint, and honor intervalSeconds.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 8: Oct 22 — Latest status endpoint and per-target intervals

**Goal:** One request should show the current state of every target, and each target
should be checked on its own interval instead of all of them every minute. Both need
the same answer: "what was each target's last check?"

**Did:** Added GET /targets/status, which returns every target with its latest result
and one clear status: UP, DOWN, PAUSED or PENDING. The scheduler now wakes every 10 s
and checks only the targets that are due, honoring intervalSeconds.

**Decisions:**
- One query for "latest result per target" (Postgres DISTINCT ON), shared by the
  status endpoint and the scheduler.
- Native SQL, so the code is tied to Postgres. Accepted: it's the only database I use.
- PAUSED wins over the last result (an old "UP" would mislead).
  PENDING = active but never checked.
- The status page shows errorType, not the full error message.
- 10 s wake-up = the precision of intervals. Wake-ups with nothing due don't log.

**Found:**
- N+1 problem: the first version ran 1 query for targets + 1 per target
  (10 queries for 9 targets). The DISTINCT ON version runs 2, whatever the count.
- The scheduler's queries got mixed into my count. Told them apart by the
  "where active=true" filter.

**Tests:**
| Test                                  | Result                              |
|---------------------------------------|-------------------------------------|
| Status endpoint, 9 targets            | 10 queries → 2                      |
| All four states                       | UP 6, 11 · DOWN 12, 14 · PAUSED 18–22 · PENDING 23 (new) |
| GitHub, 120 s                         | gap 2:00.9                          |
| Example, 60 s                         | gaps 1:00.3 – 1:01.6                |
| Example NET, 30 s (changed in SQL live)| gaps 30.1 – 31.1 s, no restart      |

**Learned:**
- How to spot N+1 in the Hibernate log, and fix it with one query.
- DISTINCT ON keeps the first row per group; ORDER BY decides which row is first.
- Derived queries: findFirstByTargetIdOrderByCheckedAtDesc writes its own SQL.
- Window functions: LAG(...) OVER (PARTITION BY ... ORDER BY ...) to measure gaps
  between rows.
- Kotlin: when, smart casts, associate / associateBy.
- Reading target.id on a lazy relation doesn't load the target.
- Gaps land at 30.x s, not 30–40 s: wake-ups line up with the previous check.
  A 45 s interval would give ~50 s gaps.

**Known limitations:**
- Wake-ups with nothing due still run 2 queries: ~12 wasted queries a minute.
- DISTINCT ON reads the whole check_results table, which grows ~8,000 rows a day
  at current settings. Week 6: index on (target_id, checked_at).
- Checks are still sequential.
- No endpoint to change a target's interval (did it in SQL today).
- Connect timeout (target 19) still unexplained.

**Next:** Thu Oct 29: uptime % over the last 24 hours, as a SQL aggregation.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 9: Oct 22 — Uptime % over the last 24 hours

**Goal:** Show each target's uptime over the last 24 hours as a percentage, calculated
by Postgres in one query, and prove the numbers are right by checking the raw rows.

**Did:** Wrote the query in psql first, checked it by hand, then added GET /targets/uptime
(repository native query + projection interface + controller).

**Decisions:**
- Uptime = up checks / all checks in a rolling 24 h window, × 100, rounded to 2 decimals.
- Every target is listed. No checks in 24 h → uptimePercent = null ("no data", not 0% or 100%).
- Paused targets are included; their uptime covers only the checks they had.
- All math in SQL, returned through a projection interface (no entity needed).
- BigDecimal for the percentage, so 98.08 stays exact.

**Found:**
- GitHub and example.org each had 1 failed check, 2 s apart (Oct 21, 22:22 UTC).
  Two unrelated sites failing together = most likely my own network, not the sites.
  Can't prove it: the rows are from before Day 7's error_type fix ("ConnectException: no details").

**Broke (on purpose):**
- 24 h filter in WHERE instead of ON: the never-checked target disappeared. WHERE drops
  the LEFT JOIN's empty row because NULL >= anything is not true.
- COUNT(*) instead of COUNT(c.id): the never-checked target showed 1 check.
- Both gave believable output with no error.

**Tests:**
| Test                           | Result                                      |
|--------------------------------|---------------------------------------------|
| Hand check, GitHub             | 52 / 53 = 98.11 ✓                           |
| Hand check, Example            | 58 / 59 = 98.31 ✓                           |
| Never-checked target (24)      | 0 checks, uptimePercent null ✓              |
| curl vs psql, all 11 targets   | identical                                   |

**Learned:**
- GROUP BY + COUNT(*) FILTER (WHERE ...) to count a subset per group.
- 100.0 instead of 100 to avoid whole-number division.
- LEFT JOIN keeps rows with no match; conditions on the joined table go in ON.
- COUNT(column) skips NULLs, COUNT(*) doesn't.
- NULLIF(x, 0) avoids division by zero.
- Postgres lowercases unquoted aliases; quote them to keep camelCase for projections.
- Projection interfaces: Spring fills them by matching property names to column aliases.

**Known limitations:**
- Uptime counts checks, not time: hours when the app was off don't count at all.
- A target with a 30 s interval has twice the weight in rows of a 60 s one (fine per target,
  but not comparable as "minutes down").
- If my own network drops, every target looks down. Real monitors check from several places.
- The window is fixed at 24 h (no ?hours= parameter yet).
- Reads the whole check_results table, like DISTINCT ON. Week 6: index on (target_id, checked_at).

**Next:** Fri Oct 30: unit tests for the checker (success, timeout, error status), mocking the HTTP call.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 10 (Thu Oct 22, plan: Fri Oct 30): unit tests for the checker

**Goal:** (your 2–3 sentences)

**Did:** Moved HttpClient into a @Bean (HttpClientConfig) and injected it into CheckService.
Added MockK. Wrote CheckServiceTest: 6 unit tests that fake the HTTP call and the repository,
so they run with no network, database or Spring.

**Decisions:**
- MockK, not Mockito: made for Kotlin. Mockito's any() returns null, which breaks non-null params.
- HttpClient injected through the constructor, so the app gets the real one and tests get a fake.
- The repository is mocked too: save() just returns what it was given.
- Each test is arrange / act / assert. JUnit makes a new test object per test, so fakes never leak.

**Broke:**
- Put CheckServiceTest.kt in src/main: every MockK/JUnit import was "Unresolved reference".
  Test libraries (testImplementation) are only visible in src/test. Moved it.
- ./gradlew test fails on Spring's generated contextLoads(): it starts the whole app,
  and without the local profile there's no database. Fixing that is Day 11.
- On purpose: swapped the first two lines of classify(). The connect-timeout test failed
  (expected CONNECT_TIMEOUT, got REQUEST_TIMEOUT), the other 5 passed. Undid it with git restore.

**Tests:**
| Test                                   | Result |
|----------------------------------------|--------|
| 200 → up, no error                     | pass   |
| 500 → down, HTTP_STATUS                | pass   |
| Request timeout → REQUEST_TIMEOUT      | pass   |
| Connect timeout → CONNECT_TIMEOUT      | pass   |
| DNS wrapped in ConnectException → DNS  | pass   |
| Invalid URL → INVALID_URL, no request  | pass   |

**Learned:**
- Unit test (one class, fakes for everything else) vs integration test (real database etc.).
- A mock only does what you tell it: every { ... } returns / throws.
- Dependency injection is what makes code testable: you can't swap something a class builds itself.
- src/main vs src/test are separate source sets with separate dependencies.
- A test that never fails proves nothing: break the code once and watch it fail.
- Commit before experimenting, then git restore <file> undoes the experiment.

**Known limitations:**
- Tests prove the labels are right *given* an exception. They don't prove the real HttpClient
  throws those exceptions; the Day 7 manual tests with bad URLs covered that.
- responseTimeMs isn't really tested: the fake answers instantly.
- SSL and connection refused have no unit tests yet.
- ./gradlew test (all tests) still fails because of contextLoads(). Day 11.

**Next:** Mon Nov 2: an integration test against a real Postgres with Testcontainers.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 11 (Thu Oct 22, plan: Mon Nov 2): integration tests with a real Postgres

**Goal:** (your 2–3 sentences)

**Did:** Added Testcontainers. TestcontainersConfiguration starts a throwaway postgres:18
for any test that imports it. Fixed Spring's contextLoads() with it, and wrote UptimeQueryTest:
inserts check rows with a known answer and checks findUptimeLast24h() returns exactly that.
./gradlew test (all 8 tests) passes for the first time.

**Decisions:**
- Real Postgres, not H2: DISTINCT ON and COUNT(*) FILTER are Postgres-only, so H2 can't run them.
- Same version as the dev container (postgres:18), so tests and app run on the same database.
- @ServiceConnection: Spring reads the container's random port/user/password itself.
  No credentials in test config, and the real database is never touched.
- @DataJpaTest for the query test: only the database layer, no scheduler adding rows mid-test.
  Each test is rolled back, so the database stays empty between tests.
- The test sets checked_at itself (1–4 h, 25 h, 30 h ago) so the right answer is known in advance.

**Broke:**
- "Redundant call of conversion method" warnings: totalChecks/upChecks are already Long.
  Removed .toLong() and compared with 4L, 0L. uptimePercent is a BigDecimal, so .toDouble() stays.
- On purpose: moved the 24 h filter from ON to WHERE (the Day 9 mistake). The test failed at the
  target-id list: "Never checked" and "Only old checks" disappeared. Undid it with git restore.

**Tests:**
| Test                                     | Result |
|------------------------------------------|--------|
| contextLoads (whole app, real Postgres)  | pass   |
| Mostly up: 4 checks, 3 up, 75.00%        | pass   |
| 25 h-old row ignored                     | pass   |
| Never checked: kept, uptime null         | pass   |
| Only old checks: kept, uptime null       | pass   |
| 6 CheckService unit tests (Day 10)       | pass   |

**Learned:**
- Unit test = one class with fakes. Integration test = real pieces together (repository + Hibernate + Postgres).
- Testcontainers starts a container per test run and Ryuk cleans it up, even after a crash.
- contextLoads passing proves every bean wires up (it confirmed the Day 10 HttpClient refactor).
- saveAndFlush: native SQL reads the database directly, so rows must be sent before the query.
- Two test types = two Spring contexts = two Hikari pools and two containers.
- Kotlin: 4 is an Int, 4L is a Long, and assertEquals treats them as different.

**Known limitations:**
- Each Spring context starts its own container, so the full test run is slower than it needs to be.
- findLatestPerTarget() (DISTINCT ON) has no integration test yet.
- Tests need Docker running. GitHub Actions (Week 5) has Docker, so CI will work.

**Next:** Tue Nov 3: a multi-stage Dockerfile for the app, run against the Postgres container.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 12 (Oct 22–23, plan: Tue Nov 3): a multi-stage Dockerfile

**Goal:** (your 2–3 sentences)

**Did:** Wrote a two-stage Dockerfile: eclipse-temurin:21-jdk builds the jar with ./gradlew bootJar,
eclipse-temurin:21-jre runs it as a non-root user. Added .dockerignore. Created the uptime-net
network, connected the existing Postgres container to it, and ran the app container with the
database settings as env vars. Health UP, real targets returned, and the scheduler checks from
inside the container.

**Decisions:**
- Multi-stage: the JDK, Gradle and the source stay in stage 1. The final image is the JRE + one jar.
- bootJar, no tests in the image build: the integration tests need Docker, which the build doesn't have.
  Tests run locally and in CI (Week 5).
- Settings come in as env vars (SPRING_DATASOURCE_*), never baked into the image. Same image works anywhere.
- .dockerignore excludes application-local.properties: .gitignore doesn't apply to Docker, so the
  password would otherwise end up inside the jar.
- User-defined network: containers find each other by name through Docker's DNS. Compose works the same way.
- Non-root user. Exec-form ENTRYPOINT so docker stop shuts Spring down cleanly.
- Cache mount for /root/.gradle: libraries survive between builds without going into the image.

**Broke:**
- First build took 42 min: downloading both Java images, Gradle and all the libraries. One-time cost.
- On purpose: ran a copy with jdbc:postgresql://localhost:5432. "Connection to localhost:5432 refused",
  container Exited (1). Inside a container, localhost is the container itself. -p 5432:5432 publishes
  Postgres to Windows, not to other containers.

**Tests:**
| Check                                  | Result                                   |
|----------------------------------------|------------------------------------------|
| docker build                           | FINISHED (18/18)                         |
| /actuator/health from the container    | UP                                       |
| /targets/status                        | real targets, lastCheckedAt after start  |
| Both containers on uptime-net          | yes                                      |
| Database URL = localhost               | refused, Exited (1)                      |

**Learned:**
- Dockerfile = recipe, image = the built result, container = a running copy of an image.
- Each Dockerfile line is a layer; unchanged layers are reused on the next build.
- Every container has its own localhost.
- Refused = the host replied "port closed". Timeout = no reply at all.
- -p publishes a port to the host only.
- Spring maps env vars to properties (SPRING_DATASOURCE_URL → spring.datasource.url), and env vars win.

**Known limitations:**
- Password typed on the command line, so it's in shell history. Day 13: a .env file with Compose.
- Network setup is manual (network create + connect). Day 13: Compose does it.
- If Postgres isn't ready, the app exits instead of waiting. Day 13: health check.
- No JVM memory cap yet (-Xmx300m comes in Week 4).

**Next:** Wed Nov 4: docker-compose.yml with the app and Postgres, and README v1.
EOF

cat >> DEVLOG.md << 'EOF'

## Day 13 (Fri Oct 23, plan: Wed Nov 4): Docker Compose + README v1

**Goal:** (your 2–3 sentences)

**Did:** Wrote docker-compose.yml: postgres:18 with a named volume and a pg_isready health check,
and the app built from the Dockerfile, waiting for Postgres with condition: service_healthy.
Secrets in .env (gitignored), .env.example committed. Replaced the README with v1.
Removed Day 12's manual app container and uptime-net; stopped the old Postgres (data kept).

**Decisions:**
- Compose gets a fresh volume (uptime-monitor_pgdata), so it proves the app works from an empty
  database. The old container is stopped, not deleted: Days 1–12 history is in uptime-monitor-pgdata.
- Fixed project name (name: uptime-monitor): same container and volume names in any clone folder.
- Postgres published on 127.0.0.1 only: reachable from my machine, not from the network.
- Same user, password and database name as before, so bootRun, psql and tests work unchanged.
- restart: unless-stopped: containers come back after a crash or a reboot.

**Broke:**
- Stopped Postgres under the running app: health DOWN, app stayed up. Started it again: UP,
  the pool reconnected by itself. (Day 12: no database at startup = crash.)
- down then up: the first curl printed nothing. The request came before Spring finished starting,
  and -s hid the error. Container "Started" is not app "ready". Redid it with sleep 10 and -sS: data survived.
- down -v: volume deleted, targets gone ([]). That's the only command that loses data.
- Found an empty uptime-monitor/ folder in the project root (never tracked). Removed it.

**Tests:**
| Check                                    | Result                                 |
|------------------------------------------|----------------------------------------|
| docker compose up                        | Postgres healthy, then app started     |
| Empty database                           | Hibernate created both tables          |
| Example + GitHub / Broken                | UP / DOWN (DNS)                        |
| Stop Postgres while running              | health DOWN, app up; back to UP        |
| down + up                                | data kept                              |
| down -v                                  | data gone                              |
| Rebuild with no code change              | 2.6 s, every step cached               |

**Learned:**
- Compose = Day 12's manual steps in one file. One network per project; service names are DNS names.
- depends_on alone waits for the container to start; service_healthy waits for the health check.
- Named volumes survive down; down -v deletes them.
- Compose reads .env automatically to fill in ${...}.
- Container started is not app ready. curl -s hides errors; -sS shows them.

**Known limitations:**
- No health check on the app itself, so Docker can't tell when Spring is ready.
- Port 8080 is open on all interfaces. Nginx goes in front in Week 4.
- bootRun and the Compose app both want port 8080: run one at a time.
- .env has to be created by hand from .env.example on each machine.

**Next:** Thu Nov 5: AWS Free plan check, $1 budget alert, launch a t3.micro Ubuntu instance.
EOF