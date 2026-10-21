package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.TargetRepository
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/targets")
class StatusController(
    private val targetRepository: TargetRepository,
    private val checkResultRepository: CheckResultRepository,
) {
    // 2 queries total, however many targets there are.
    @GetMapping("/status")
    fun status(): List<TargetStatusResponse> {
        val targets = targetRepository.findAll(Sort.by("id"))

        // Map of target id -> its newest result, built in memory.
        val latestByTargetId = checkResultRepository.findLatestPerTarget()
            .associateBy { it.target.id }

        return targets.map { target ->
            TargetStatusResponse.from(target, latestByTargetId[target.id])
        }
    }
}