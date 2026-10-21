package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.Target
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

@Service
class CheckService(
    private val checkResultRepository: CheckResultRepository,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun check(target: Target): CheckResult {
        val checkedAt = Instant.now()
        val start = System.nanoTime()

        val result = try {
            val request = HttpRequest.newBuilder(URI.create(target.url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            // ofInputStream() returns as soon as the status code and headers arrive,
            // before the page itself is downloaded.
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            // We don't need the page, so close it without reading it.
            response.body().close()

            CheckResult(
                target = target,
                checkedAt = checkedAt,
                up = response.statusCode() in 200..399,
                statusCode = response.statusCode(),
                responseTimeMs = elapsedMs,
            )
        } catch (e: IOException) {
            failedResult(target, checkedAt, e)
        } catch (e: IllegalArgumentException) {
            failedResult(target, checkedAt, e)
        }

        return checkResultRepository.save(result)
    }

    private fun failedResult(target: Target, checkedAt: Instant, e: Exception) = CheckResult(
        target = target,
        checkedAt = checkedAt,
        up = false,
        error = "${e.javaClass.simpleName}: ${e.message ?: "no details"}".take(500),
    )
}