package io.github.malekbaghdadi.uptime_monitor.target

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateTargetRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String = "",

    @field:NotBlank
    @field:Size(max = 2048)
    @field:Pattern(
        regexp = """https?://\S+""",
        message = "must start with http:// or https://",
    )
    val url: String = "",

    @field:Min(30)
    @field:Max(3600)
    val intervalSeconds: Int = 60,
)