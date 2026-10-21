package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.Target
import java.time.Instant

// One row of the status page: the target, plus its latest check (if any).
data class TargetStatusResponse(
    val targetId: Long?,
    val name: String,
    val url: String,
    val status: TargetStatus,
    val active: Boolean,
    val intervalSeconds: Int,
    // Everything below is null if the target has never been checked.
    val lastCheckedAt: Instant?,
    val up: Boolean?,
    val statusCode: Int?,
    val responseTimeMs: Long?,
    val errorType: ErrorType?,
) {
    companion object {
        fun from(target: Target, last: CheckResult?) = TargetStatusResponse(
            targetId = target.id,
            name = target.name,
            url = target.url,
            status = statusOf(target, last),
            active = target.active,
            intervalSeconds = target.intervalSeconds,
            lastCheckedAt = last?.checkedAt,
            up = last?.up,
            statusCode = last?.statusCode,
            responseTimeMs = last?.responseTimeMs,
            errorType = last?.errorType,
        )

        // Order matters: the first matching line wins.
        private fun statusOf(target: Target, last: CheckResult?): TargetStatus = when {
            !target.active -> TargetStatus.PAUSED
            last == null -> TargetStatus.PENDING
            last.up -> TargetStatus.UP
            else -> TargetStatus.DOWN
        }
    }
}