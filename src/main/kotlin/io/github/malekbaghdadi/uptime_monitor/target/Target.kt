package io.github.malekbaghdadi.uptime_monitor.target

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "targets",
    uniqueConstraints = [UniqueConstraint(name = "uk_targets_url", columnNames = ["url"])],
)
class Target(
    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, length = 2048)
    var url: String,

    @Column(nullable = false)
    var intervalSeconds: Int = 60,

    @Column(nullable = false)
    var active: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)