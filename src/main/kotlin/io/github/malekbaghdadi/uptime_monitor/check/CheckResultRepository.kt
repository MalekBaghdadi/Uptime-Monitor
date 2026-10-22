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

    // Uptime % per target over the last 24 hours, in ONE query.
    // - LEFT JOIN keeps targets with no recent checks (they get uptimePercent = null).
    // - The 24 h filter is in ON, not WHERE: in WHERE it would drop those targets.
    // - COUNT(c.id), not COUNT(*): COUNT(*) would count the empty row as 1 check.
    // - NULLIF(..., 0) turns "0 checks" into null instead of a division-by-zero error.
    // Aliases are in double quotes so Postgres keeps their camelCase; they must match TargetUptime.
    @Query(
        value = """
            SELECT t.id                              AS "targetId",
                   t.name                            AS "name",
                   COUNT(c.id)                       AS "totalChecks",
                   COUNT(c.id) FILTER (WHERE c.up)   AS "upChecks",
                   ROUND(100.0 * COUNT(c.id) FILTER (WHERE c.up) / NULLIF(COUNT(c.id), 0), 2)
                                                     AS "uptimePercent"
            FROM targets t
            LEFT JOIN check_results c
                   ON c.target_id = t.id
                  AND c.checked_at >= now() - interval '24 hours'
            GROUP BY t.id, t.name
            ORDER BY t.id
        """,
        nativeQuery = true,
    )
    fun findUptimeLast24h(): List<TargetUptime>
}