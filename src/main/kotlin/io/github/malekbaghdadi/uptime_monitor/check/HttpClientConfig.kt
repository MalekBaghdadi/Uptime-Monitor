package io.github.malekbaghdadi.uptime_monitor.check

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

// Builds the one HttpClient the app uses.
// Spring passes it to anything that asks for an HttpClient in its constructor.
@Configuration
class HttpClientConfig {

    @Bean
    fun httpClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
}