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