package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.common.NotFoundException
import io.github.malekbaghdadi.uptime_monitor.target.TargetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// Temporary: replaced by the scheduled checker on Monday (Week 2).
@RestController
@RequestMapping("/targets")
class TempCheckController(
    private val targetRepository: TargetRepository,
    private val checkService: CheckService,
) {
    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.CREATED)
    fun check(@PathVariable id: Long): CheckResultResponse {
        val target = targetRepository.findByIdOrNull(id)
            ?: throw NotFoundException("Target", id)
        return CheckResultResponse.from(checkService.check(target))
    }
}