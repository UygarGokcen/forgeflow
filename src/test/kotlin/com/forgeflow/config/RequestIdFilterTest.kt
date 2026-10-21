package com.forgeflow.config

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class RequestIdFilterTest {

	private val filter = RequestIdFilter()

	/** Runs the filter and returns the request id the rest of the chain saw in the MDC. */
	private fun run(request: MockHttpServletRequest, response: MockHttpServletResponse): String? {
		var seen: String? = null
		val chain = FilterChain { _, _ -> seen = MDC.get(MDC_REQUEST_ID) }
		filter.doFilter(request, response, chain)
		return seen
	}

	@Test
	fun `a request without an id gets a fresh one, in the MDC and the response header`() {
		val response = MockHttpServletResponse()

		val seen = run(MockHttpServletRequest(), response)

		UUID.fromString(seen)
		assertEquals(seen, response.getHeader(REQUEST_ID_HEADER))
	}

	@Test
	fun `an id from the caller is kept`() {
		val request = MockHttpServletRequest().apply { addHeader(REQUEST_ID_HEADER, "gateway-abc.123") }
		val response = MockHttpServletResponse()

		assertEquals("gateway-abc.123", run(request, response))
		assertEquals("gateway-abc.123", response.getHeader(REQUEST_ID_HEADER))
	}

	@Test
	fun `an id that could forge log lines is replaced`() {
		val forged = "x\n2026-01-01 ERROR fake log line"
		val request = MockHttpServletRequest().apply { addHeader(REQUEST_ID_HEADER, forged) }

		assertNotEquals(forged, run(request, MockHttpServletResponse()))
	}

	@Test
	fun `the id is cleared once the request is done, so a pooled thread doesn't carry it over`() {
		run(MockHttpServletRequest(), MockHttpServletResponse())

		assertNull(MDC.get(MDC_REQUEST_ID))
	}
}
