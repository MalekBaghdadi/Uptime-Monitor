package io.github.malekbaghdadi.uptime_monitor.common

class NotFoundException(resource: String, id: Long) :
    RuntimeException("$resource $id not found")