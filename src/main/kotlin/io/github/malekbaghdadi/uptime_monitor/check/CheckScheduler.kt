package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.TargetRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CheckScheduler(
    private val targetRepository: TargetRepository,
    private val checkService: CheckService,
) {
    private val log = LoggerFactory.getLogger(CheckScheduler::class.java)

    // fixedDelay: wait 60 s after the previous run FINISHES, so runs never overlap.
    // initialDelay: wait 10 s after startup before the first run.
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    fun checkAllActiveTargets() {
        val targets = targetRepository.findByActiveTrue()
        val start = System.nanoTime()
        var up = 0
        var down = 0
        var crashed = 0

        for (target in targets) {
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
            "Checked {} targets in {} ms: {} up, {} down, {} crashed",
            targets.size, elapsedMs, up, down, crashed,
        )
    }
}