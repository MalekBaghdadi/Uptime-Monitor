package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.Target
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLException

@Service
class CheckService(
    private val checkResultRepository: CheckResultRepository,
    // Passed in, not built here: the app gets the real one (HttpClientConfig),
    // tests get a fake one.
    private val httpClient: HttpClient,
) {

    fun check(target: Target): CheckResult {
        val checkedAt = Instant.now()
        val start = System.nanoTime()

        val result = try {
            val request = HttpRequest.newBuilder(URI.create(target.url))
                .timeout(Duration.ofSeconds(10))
                // Say who we are. Some sites (e.g. Wikipedia) block Java's default User-Agent.
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()

            // ofInputStream() returns as soon as the status code and headers arrive,
            // before the page itself is downloaded.
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            // We don't need the page, so close it without reading it.
            response.body().close()

            val status = response.statusCode()
            val up = status in 200..399

            CheckResult(
                target = target,
                checkedAt = checkedAt,
                up = up,
                statusCode = status,
                responseTimeMs = elapsedMs,
                // The site answered, but with an error code (e.g. 404, 500).
                errorType = if (up) null else ErrorType.HTTP_STATUS,
            )
        } catch (e: IOException) {
            failedResult(target, checkedAt, e)
        } catch (e: IllegalArgumentException) {
            failedResult(target, checkedAt, e)
        }

        return checkResultRepository.save(result)
    }

    private fun failedResult(target: Target, checkedAt: Instant, e: Exception): CheckResult {
        // The error, then its cause, then the cause's cause...
        // take(10) is a safety limit in case a chain is very long.
        val chain = generateSequence<Throwable>(e) { it.cause }.take(10).toList()

        return CheckResult(
            target = target,
            checkedAt = checkedAt,
            up = false,
            errorType = classify(chain),
            error = describe(chain).take(500),
        )
    }

    // Order matters, most specific first:
    // - HttpConnectTimeoutException is a kind of HttpTimeoutException.
    // - A DNS failure arrives wrapped inside a ConnectException.
    // - UnresolvedAddressException is a kind of IllegalArgumentException.
    private fun classify(chain: List<Throwable>): ErrorType = when {
        chain.any { it is HttpConnectTimeoutException } -> ErrorType.CONNECT_TIMEOUT
        chain.any { it is HttpTimeoutException } -> ErrorType.REQUEST_TIMEOUT
        chain.any { it is UnresolvedAddressException || it is UnknownHostException } -> ErrorType.DNS
        chain.any { it is SSLException } -> ErrorType.SSL
        chain.any { it is ConnectException } -> ErrorType.CONNECTION_REFUSED
        chain.any { it is IllegalArgumentException } -> ErrorType.INVALID_URL
        else -> ErrorType.OTHER
    }

    // Keep the outer error (what failed) and the root cause (why).
    // The ones in between mostly repeat the same message.
    // e.g. "ConnectException: no details <- UnresolvedAddressException: no details"
    private fun describe(chain: List<Throwable>): String {
        val parts = if (chain.size == 1) chain else listOf(chain.first(), chain.last())
        return parts.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message ?: "no details"}" }
    }

    companion object {
        // App name/version, plus a link where a site owner can find out who we are.
        private const val USER_AGENT =
            "UptimeMonitor/0.1 (+https://github.com/malekbaghdadi/uptime-monitor)"
    }
}