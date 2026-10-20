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