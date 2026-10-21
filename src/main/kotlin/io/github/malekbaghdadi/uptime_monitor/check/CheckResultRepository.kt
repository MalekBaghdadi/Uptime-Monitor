package io.github.malekbaghdadi.uptime_monitor.check

import org.springframework.data.jpa.repository.JpaRepository

interface CheckResultRepository : JpaRepository<CheckResult, Long>