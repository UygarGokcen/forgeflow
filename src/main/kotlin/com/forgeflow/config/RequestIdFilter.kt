package com.forgeflow.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

const val REQUEST_ID_HEADER = "X-Request-ID"
const val MDC_REQUEST_ID = "requestId"

/** A caller-supplied id is only reused if it's short and plain, so it can't inject into logs. */
private val SAFE_REQUEST_ID = Regex("^[A-Za-z0-9._-]{1,64}$")

/**
 * Gives every request an id, puts it in the logging MDC for the whole request and echoes it back
 * in the `X-Request-ID` response header, so a user's error report can be matched to log lines.
 *
 * An id sent by the caller (or a gateway in front) is kept, which lets one id follow a request
 * across services. Runs before everything else, Spring Security included.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf { SAFE_REQUEST_ID.matches(it) }
			?: UUID.randomUUID().toString()
		MDC.put(MDC_REQUEST_ID, requestId)
		response.setHeader(REQUEST_ID_HEADER, requestId)
		try {
			filterChain.doFilter(request, response)
		} finally {
			// Servlet threads are pooled; leaving this set would label the next request's logs.
			MDC.remove(MDC_REQUEST_ID)
		}
	}
}
