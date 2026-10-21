package io.github.malekbaghdadi.uptime_monitor.check

// The one-word state of a target on the status page.
enum class TargetStatus {
    UP,      // last check succeeded
    DOWN,    // last check failed
    PAUSED,  // monitoring is turned off for this target
    PENDING, // active, but not checked yet
}