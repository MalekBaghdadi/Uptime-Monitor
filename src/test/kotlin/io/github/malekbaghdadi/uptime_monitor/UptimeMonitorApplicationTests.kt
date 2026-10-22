package io.github.malekbaghdadi.uptime_monitor

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// Starts the WHOLE app (every bean, scheduler included) against the test Postgres.
// If any bean can't be created or wired, this fails.
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class UptimeMonitorApplicationTests {

    @Test
    fun contextLoads() {
        // Empty on purpose: the test passes if the app starts.
    }
}