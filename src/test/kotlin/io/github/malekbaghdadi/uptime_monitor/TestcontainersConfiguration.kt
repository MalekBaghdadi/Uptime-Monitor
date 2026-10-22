package io.github.malekbaghdadi.uptime_monitor

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

// Any test that imports this gets a real Postgres 18 in a throwaway container.
// Same version as uptime-monitor-postgres, so tests run on the same database as the app.
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // Spring starts the container before the app and stops it at the end.
    // @ServiceConnection: Spring reads the container's random port, user and password
    // and uses them as spring.datasource.*.
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
}