package io.github.malekbaghdadi.uptime_monitor.target

import org.springframework.data.jpa.repository.JpaRepository

interface TargetRepository : JpaRepository<Target, Long> {
    fun existsByUrl(url: String): Boolean

    fun findByActiveTrue(): List<Target>
}