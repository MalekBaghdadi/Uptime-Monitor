package io.github.malekbaghdadi.uptime_monitor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UptimeMonitorApplication

fun main(args: Array<String>) {
	runApplication<UptimeMonitorApplication>(*args)
}
