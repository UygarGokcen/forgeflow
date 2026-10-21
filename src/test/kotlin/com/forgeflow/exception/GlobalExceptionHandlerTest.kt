package com.forgeflow.exception

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.forgeflow.domain.Material
import com.forgeflow.domain.Product
import com.forgeflow.domain.UnitOfMeasure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.data.mapping.PropertyReferenceException
import org.springframework.data.util.TypeInformation
import org.springframework.http.HttpMethod
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.UUID

class GlobalExceptionHandlerTest {

	private val handler = GlobalExceptionHandler()
	private val request = MockHttpServletRequest("PUT", "/api/v1/materials/1")

	@Test
	fun `losing an optimistic-lock race is a 409, not a 500`() {
		val ex = ObjectOptimisticLockingFailureException(Material::class.java, UUID.randomUUID())

		val response = handler.handleConcurrentUpdate(ex, request)

		assertEquals(409, response.statusCode.value())
	}

	@Test
	fun `an unknown sort property is a 400, whether or not Spring Data wrapped it`() {
		val propertyError = PropertyReferenceException("nope", TypeInformation.of(Product::class.java), emptyList())

		assertEquals(400, handler.handleBadSort(propertyError, request).statusCode.value())
		assertEquals(
			400,
			handler.handleBadSort(InvalidDataAccessApiUsageException("wrapped", propertyError), request).statusCode.value(),
		)
	}

	@Test
	fun `client mistakes Spring MVC detects keep their 4xx instead of becoming a 500`() {
		fun status(ex: Exception) = handler.handleUnexpected(ex, request).statusCode.value()

		assertEquals(404, status(NoResourceFoundException(HttpMethod.GET, "api/v1/nope")))
		assertEquals(405, status(HttpRequestMethodNotSupportedException("DELETE")))
		assertEquals(415, status(HttpMediaTypeNotSupportedException("text/plain")))
		assertEquals(400, status(HttpMessageNotReadableException("bad json", MockHttpInputMessage(ByteArray(0)))))
	}

	@Test
	fun `a body with a bad value names the field it was in`() {
		val cause = InvalidFormatException.from(null, "not one of the values", "BANANA", UnitOfMeasure::class.java)
			.apply { prependPath(Object(), "unitOfMeasure") }
		val ex = HttpMessageNotReadableException("bad enum", cause, MockHttpInputMessage(ByteArray(0)))

		val response = handler.handleUnexpected(ex, request)

		assertEquals(400, response.statusCode.value())
		assertEquals("Invalid value for 'unitOfMeasure'", response.body!!.message)
	}

	@Test
	fun `a method-security denial is a 403`() {
		assertEquals(403, handler.handleAccessDenied(AccessDeniedException("no"), request).statusCode.value())
	}

	@Test
	fun `a genuine bug is still a 500`() {
		assertEquals(500, handler.handleUnexpected(IllegalStateException("boom"), request).statusCode.value())
	}

	@Test
	fun `an unrelated data-access error is still treated as unexpected`() {
		val response = handler.handleBadSort(InvalidDataAccessApiUsageException("something else"), request)

		assertEquals(500, response.statusCode.value())
	}
}
