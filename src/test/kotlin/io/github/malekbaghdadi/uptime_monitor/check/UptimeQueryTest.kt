package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.TestcontainersConfiguration
import io.github.malekbaghdadi.uptime_monitor.target.Target
import io.github.malekbaghdadi.uptime_monitor.target.TargetRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

// @DataJpaTest loads only the database layer: entities, repositories, Hibernate.
// No web server, no scheduler, so no surprise rows appear mid-test.
// Each test runs in a transaction that is rolled back at the end, so the database stays empty.
@DataJpaTest
@Import(TestcontainersConfiguration::class)
class UptimeQueryTest @Autowired constructor(
    private val targetRepository: TargetRepository,
    private val checkResultRepository: CheckResultRepository,
) {

    @Test
    fun `uptime counts only the last 24 hours and keeps targets with no recent checks`() {
        // ---- Arrange: data where we already know the answer ----
        val mostlyUp = targetRepository.saveAndFlush(Target(name = "Mostly up", url = "https://a.test"))
        val neverChecked = targetRepository.saveAndFlush(Target(name = "Never checked", url = "https://b.test"))
        val onlyOld = targetRepository.saveAndFlush(Target(name = "Only old checks", url = "https://c.test"))

        checkResultRepository.saveAllAndFlush(
            listOf(
                // Inside the 24 h window: 3 up, 1 down.
                result(mostlyUp, hoursAgo = 1, up = true),
                result(mostlyUp, hoursAgo = 2, up = true),
                result(mostlyUp, hoursAgo = 3, up = true),
                result(mostlyUp, hoursAgo = 4, up = false),
                // Outside the window: must be ignored.
                result(mostlyUp, hoursAgo = 25, up = false),
                result(onlyOld, hoursAgo = 30, up = true),
            )
        )
        // saveAndFlush: send the INSERTs to Postgres now. The uptime query is native SQL,
        // so it reads the database directly and wouldn't see rows still waiting in Hibernate.

        // ---- Act ----
        val rows = checkResultRepository.findUptimeLast24h()

        // ---- Assert ----
        // All 3 targets must be there, in id order (the query sorts by t.id).
        assertEquals(listOf(mostlyUp.id, neverChecked.id, onlyOld.id), rows.map { it.targetId })

        val (a, b, c) = rows

        // Mostly up: the 25 h-old row is not counted.
        assertEquals(4L, a.totalChecks)
        assertEquals(3L, a.upChecks)
        // uptimePercent is a BigDecimal; compare as a number.
        assertEquals(75.0, a.uptimePercent?.toDouble())

        // Never checked: kept by the LEFT JOIN, no data → null, not 0%.
        assertEquals(0L, b.totalChecks)
        assertNull(b.uptimePercent)

        // Only old checks: also kept, also null.
        assertEquals(0L, c.totalChecks)
        assertNull(c.uptimePercent)
    }

    private fun result(target: Target, hoursAgo: Long, up: Boolean) = CheckResult(
        target = target,
        checkedAt = Instant.now().minus(hoursAgo, ChronoUnit.HOURS),
        up = up,
    )
}