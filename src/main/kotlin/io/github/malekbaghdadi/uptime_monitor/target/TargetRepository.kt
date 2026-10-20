package io.github.malekbaghdadi.uptime_monitor.target

import org.springframework.data.jpa.repository.JpaRepository

interface TargetRepository : JpaRepository<Target, Long>