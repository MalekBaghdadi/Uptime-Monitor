# Uptime Monitor

A self-hosted service that checks a list of URLs on a schedule, stores every result in PostgreSQL,
and reports each site's current status and its uptime over the last 24 hours.

> Work in progress: a 6-week learning project. The day-by-day story is in [DEVLOG.md](DEVLOG.md).

## Tech stack

- Kotlin + Spring Boot 4 (Java 21)
- PostgreSQL 18
- Docker (multi-stage build) and Docker Compose
- Tests: JUnit 5, MockK, Testcontainers

## Run it

You need **Git** and **Docker Desktop** (running). Nothing else: Java and Gradle run inside Docker.

```bash
git clone https://github.com/MalekBaghdadi/Uptime-Monitor.git
cd Uptime-Monitor
cp .env.example .env          # then set your own password in .env
docker compose up -d --build
```

The first build downloads Java, Gradle and all the libraries, so it can take a while.
Later builds take seconds.

Check it's up (give Spring a few seconds after the containers start):

```bash
curl localhost:8080/actuator/health      # {"status":"UP",...}
```

## Try it

```bash
# Add a target (intervalSeconds is optional, default 60)
curl -X POST localhost:8080/targets \
  -H "Content-Type: application/json" \
  -d '{"name":"Example","url":"https://example.org","intervalSeconds":30}'

# Current status of every target
curl localhost:8080/targets/status

# Uptime % over the last 24 hours
curl localhost:8080/targets/uptime
```

## API

| Method | Path                    | What it does                                    |
|--------|-------------------------|-------------------------------------------------|
| POST   | `/targets`              | Add a target                                    |
| GET    | `/targets`              | List targets                                    |
| GET    | `/targets/{id}`         | One target                                      |
| DELETE | `/targets/{id}`         | Delete a target and all its results             |
| POST   | `/targets/{id}/pause`   | Stop checking it (keeps its history)            |
| POST   | `/targets/{id}/resume`  | Start checking it again                         |
| GET    | `/targets/status`       | Latest result per target: UP, DOWN, PAUSED, PENDING |
| GET    | `/targets/uptime`       | Uptime % per target over the last 24 hours      |
| GET    | `/actuator/health`      | App and database health                         |

## How it works

- A scheduler wakes every 10 seconds and checks only the targets that are due (each has its own interval).
- Every check is saved: status code, response time, and an error type when it fails
  (DNS, connection refused, timeout, SSL, bad HTTP status).
- Status and uptime come from single SQL queries (`DISTINCT ON`, `COUNT(*) FILTER`).
- In Compose, the app waits for a Postgres health check before starting. Data lives in a named volume.

## Stop and reset

```bash
docker compose down        # stop and remove containers; data is kept
docker compose down -v     # also delete the database volume: all data is lost
```

## Run the tests

Docker must be running: the integration tests start a throwaway PostgreSQL with Testcontainers.

```bash
./gradlew test
```
