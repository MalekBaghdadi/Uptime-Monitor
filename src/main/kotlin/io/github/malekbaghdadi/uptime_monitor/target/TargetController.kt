package io.github.malekbaghdadi.uptime_monitor.target

import io.github.malekbaghdadi.uptime_monitor.common.ConflictException
import io.github.malekbaghdadi.uptime_monitor.common.NotFoundException
import jakarta.validation.Valid
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/targets")
class TargetController(
    private val targetRepository: TargetRepository,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateTargetRequest): Target {
        if (targetRepository.existsByUrl(request.url)) {
            throw ConflictException("A target with this URL already exists.")
        }
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
    fun get(@PathVariable id: Long): Target = findTarget(id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        targetRepository.delete(findTarget(id))
    }

    @PostMapping("/{id}/pause")
    fun pause(@PathVariable id: Long): Target = setActive(id, false)

    @PostMapping("/{id}/resume")
    fun resume(@PathVariable id: Long): Target = setActive(id, true)

    private fun setActive(id: Long, active: Boolean): Target {
        val target = findTarget(id)
        target.active = active
        return targetRepository.save(target)
    }

    private fun findTarget(id: Long): Target =
        targetRepository.findByIdOrNull(id) ?: throw NotFoundException("Target", id)
}