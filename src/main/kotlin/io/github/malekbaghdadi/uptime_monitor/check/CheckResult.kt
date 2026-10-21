package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.Target
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

@Entity
@Table(name = "check_results")
class CheckResult(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var target: Target,

    @Column(nullable = false)
    var checkedAt: Instant,

    @Column(nullable = false)
    var up: Boolean,

    var statusCode: Int? = null,

    var responseTimeMs: Long? = null,

    // NULL when the check succeeded.
    // STRING stores the name ("DNS"), not the position (0, 1, 2...).
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var errorType: ErrorType? = null,

    @Column(length = 500)
    var error: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)