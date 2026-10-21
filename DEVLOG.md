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