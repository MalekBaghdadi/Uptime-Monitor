package io.github.malekbaghdadi.uptime_monitor.check

import java.time.Instant

data class CheckResultResponse(
    val id: Long?,
    val targetId: Long?,
    val checkedAt: Instant,
    val up: Boolean,
    val statusCode: Int?,
    val responseTimeMs: Long?,
    val error: String?,
) {
    companion object {
        fun from(result: CheckResult) = CheckResultResponse(
            id = result.id,
            targetId = result.target.id,
            checkedAt = result.checkedAt,
            up = result.up,
            statusCode = result.statusCode,
            responseTimeMs = result.responseTimeMs,
            error = result.error,
        )
    }
}