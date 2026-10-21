package io.github.malekbaghdadi.uptime_monitor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class UptimeMonitorApplication

fun main(args: Array<String>) {
	runApplication<UptimeMonitorApplication>(*args)
}
