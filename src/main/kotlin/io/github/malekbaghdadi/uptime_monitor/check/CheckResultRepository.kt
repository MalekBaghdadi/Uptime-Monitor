package io.github.malekbaghdadi.uptime_monitor.check

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CheckResultRepository : JpaRepository<CheckResult, Long> {

    // The newest result for every target, in ONE query.
    // DISTINCT ON keeps the first row per target_id; ORDER BY makes "first" = newest.
    // Postgres-only SQL, so it's a native query.
    @Query(
        value = """
            SELECT DISTINCT ON (target_id) *
            FROM check_results
            ORDER BY target_id, checked_at DESC
        """,
        nativeQuery = true,
    )
    fun findLatestPerTarget(): List<CheckResult>
}