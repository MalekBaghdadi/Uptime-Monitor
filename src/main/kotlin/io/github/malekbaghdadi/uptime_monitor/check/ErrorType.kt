package io.github.malekbaghdadi.uptime_monitor.check

// Why a check failed. Stored as text in Postgres (e.g. "DNS"), so it's readable and queryable.
enum class ErrorType {
    DNS,                // the domain name can't be found
    CONNECTION_REFUSED, // the machine answered, but nothing is listening on that port
    CONNECT_TIMEOUT,    // no answer while opening the connection (5 s)
    REQUEST_TIMEOUT,    // connected, but the reply didn't come in time (10 s)
    SSL,                // certificate problem: expired, wrong host, self-signed...
    HTTP_STATUS,        // the site answered, but with an error code (4xx/5xx)
    INVALID_URL,        // the URL itself can't be used
    OTHER,              // anything we haven't classified yet
}