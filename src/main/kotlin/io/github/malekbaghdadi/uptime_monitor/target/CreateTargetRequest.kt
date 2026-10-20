package io.github.malekbaghdadi.uptime_monitor.target

data class CreateTargetRequest(
    val name: String,
    val url: String,
    val intervalSeconds: Int = 60,
)
