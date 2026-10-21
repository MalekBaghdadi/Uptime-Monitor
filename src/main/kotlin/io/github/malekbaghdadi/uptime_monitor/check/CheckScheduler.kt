package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.TargetRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class CheckScheduler(
    private val targetRepository: TargetRepository,
    private val checkResultRepository: CheckResultRepository,
    private val checkService: CheckService,
) {
    private val log = LoggerFactory.getLogger(CheckScheduler::class.java)

    // Wake up every 10 s (after the previous run finishes) and check only the targets that are due.
    // 10 s is the precision: a 60 s target is checked 60–70 s after its last check.
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    fun checkDueTargets() {
        val now = Instant.now()

        // target id -> time of its last check. One query (DISTINCT ON), same as the status endpoint.
        val lastCheckedAt = checkResultRepository.findLatestPerTarget()
            .associate { it.target.id to it.checkedAt }

        val active = targetRepository.findByActiveTrue()

        // Due = never checked, or the last check is at least intervalSeconds old.
        val due = active.filter { target ->
            val last = lastCheckedAt[target.id]
            last == null || Duration.between(last, now).seconds >= target.intervalSeconds
        }

        // Nothing due: stay quiet, so the log isn't flooded with a line every 10 s.
        if (due.isEmpty()) return

        val start = System.nanoTime()
        var up = 0
        var down = 0
        var crashed = 0

        for (target in due) {
            // try/catch per target: one crash must not skip the targets after it.
            try {
                val result = checkService.check(target)
                if (result.up) up++ else down++
            } catch (e: Exception) {
                crashed++
                log.error("Check crashed for target {} ({})", target.id, target.url, e)
            }
        }

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        log.info(
            "Checked {} of {} active targets in {} ms: {} up, {} down, {} crashed (ids: {})",
            due.size, active.size, elapsedMs, up, down, crashed, due.map { it.id },
        )
    }
}