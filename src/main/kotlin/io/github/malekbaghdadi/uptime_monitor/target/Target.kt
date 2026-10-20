package io.github.malekbaghdadi.uptime_monitor.target

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "targets")
class Target(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = true)
    var url: String,

    @Column(nullable = false)
    var intervalSeconds: Int = 60,

    @Column(nullable = false)
    var active: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)