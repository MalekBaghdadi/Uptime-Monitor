package io.github.malekbaghdadi.uptime_monitor.check

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/targets")
class UptimeController(
    private val checkResultRepository: CheckResultRepository,
) {
    // Uptime % per target over the last 24 hours. One query, all the math done in Postgres.
    @GetMapping("/uptime")
    fun uptime(): List<TargetUptime> = checkResultRepository.findUptimeLast24h()
}