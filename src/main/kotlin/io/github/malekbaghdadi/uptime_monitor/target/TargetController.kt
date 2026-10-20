package io.github.malekbaghdadi.uptime_monitor.target

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable

@RestController
@RequestMapping("/targets")
class TargetController(
    private val targetRepository: TargetRepository,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateTargetRequest): Target {
        val target = Target(
            name = request.name,
            url = request.url,
            intervalSeconds = request.intervalSeconds,
        )
        return targetRepository.save(target)
    }

    @GetMapping
    fun list(): List<Target> = targetRepository.findAll()
        @GetMapping("/{id}")
    fun get(@PathVariable id: Long): Target =
        targetRepository.findById(id).orElseThrow()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        targetRepository.deleteById(id)
    }

    @PostMapping("/{id}/pause")
    fun pause(@PathVariable id: Long): Target = setActive(id, false)

    @PostMapping("/{id}/resume")
    fun resume(@PathVariable id: Long): Target = setActive(id, true)

    private fun setActive(id: Long, active: Boolean): Target {
        val target = targetRepository.findById(id).orElseThrow()
        target.active = active
        return targetRepository.save(target)
    }
}