package io.github.malekbaghdadi.uptime_monitor.check

import java.math.BigDecimal

// The shape of one row from findUptimeLast24h().
// Spring fills it in by matching each property name to a column alias in the query.
interface TargetUptime {
    val targetId: Long
    val name: String
    val totalChecks: Long
    val upChecks: Long
    val uptimePercent: BigDecimal? // null = no checks in the last 24 h
}