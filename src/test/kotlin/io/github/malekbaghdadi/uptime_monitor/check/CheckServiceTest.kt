package io.github.malekbaghdadi.uptime_monitor.check

import io.github.malekbaghdadi.uptime_monitor.target.Target
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckServiceTest {

    // Fakes: they only do what each test tells them to.
    private val httpClient = mockk<HttpClient>()
    private val repository = mockk<CheckResultRepository>()

    // The real CheckService, built with the fakes.
    private val service = CheckService(repository, httpClient)

    private val target = Target(name = "Test", url = "https://example.com")

    // JUnit creates a fresh CheckServiceTest for every test,
    // so each test starts with brand-new fakes.
    init {
        // save() returns whatever it was given, like the real one does.
        every { repository.save(any<CheckResult>()) } answers { firstArg() }
    }

    // ---- The three cases from the plan ----

    @Test
    fun `200 response is saved as up`() {
        respondWith(200)

        val result = service.check(target)

        assertTrue(result.up)
        assertEquals(200, result.statusCode)
        assertNull(result.errorType)
        assertNull(result.error)
        assertNotNull(result.responseTimeMs)
        verify(exactly = 1) { repository.save(any<CheckResult>()) }
    }

    @Test
    fun `500 response is saved as down with HTTP_STATUS`() {
        respondWith(500)

        val result = service.check(target)

        assertFalse(result.up)
        assertEquals(500, result.statusCode)
        assertEquals(ErrorType.HTTP_STATUS, result.errorType)
    }

    @Test
    fun `request timeout is saved as REQUEST_TIMEOUT`() {
        failWith(HttpTimeoutException("request timed out"))

        val result = service.check(target)

        assertFalse(result.up)
        assertNull(result.statusCode)
        assertEquals(ErrorType.REQUEST_TIMEOUT, result.errorType)
        assertEquals("HttpTimeoutException: request timed out", result.error)
    }

    // ---- Bonus: the tricky ones from Day 7 ----

    @Test
    fun `connect timeout is not mistaken for a request timeout`() {
        // HttpConnectTimeoutException IS an HttpTimeoutException,
        // so this only passes if classify() checks it first.
        failWith(HttpConnectTimeoutException("connect timed out"))

        val result = service.check(target)

        assertEquals(ErrorType.CONNECT_TIMEOUT, result.errorType)
    }

    @Test
    fun `DNS failure hidden inside a ConnectException is found`() {
        // How Java really reports it: an empty ConnectException wrapping the real cause.
        val wrapped = ConnectException().apply { initCause(UnresolvedAddressException()) }
        failWith(wrapped)

        val result = service.check(target)

        assertEquals(ErrorType.DNS, result.errorType)
        assertEquals(
            "ConnectException: no details <- UnresolvedAddressException: no details",
            result.error,
        )
    }

    @Test
    fun `invalid URL fails before any request is sent`() {
        // The space makes URI.create() throw IllegalArgumentException.
        val badTarget = Target(name = "Bad", url = "http://exa mple.com")

        val result = service.check(badTarget)

        assertEquals(ErrorType.INVALID_URL, result.errorType)
        verify(exactly = 0) { httpClient.send(any(), any<HttpResponse.BodyHandler<InputStream>>()) }
    }

    // ---- Helpers ----

    // "When send() is called, answer with this status code."
    private fun respondWith(status: Int) {
        val response = mockk<HttpResponse<InputStream>>()
        every { response.statusCode() } returns status
        every { response.body() } returns InputStream.nullInputStream()
        every { httpClient.send(any(), any<HttpResponse.BodyHandler<InputStream>>()) } returns response
    }

    // "When send() is called, throw this error."
    private fun failWith(error: Throwable) {
        every { httpClient.send(any(), any<HttpResponse.BodyHandler<InputStream>>()) } throws error
    }
}