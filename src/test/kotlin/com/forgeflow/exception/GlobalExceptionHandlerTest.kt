package com.forgeflow.exception

import com.forgeflow.domain.Material
import com.forgeflow.domain.Product
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.data.mapping.PropertyReferenceException
import org.springframework.data.util.TypeInformation
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
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
	fun `an unrelated data-access error is still treated as unexpected`() {
		val response = handler.handleBadSort(InvalidDataAccessApiUsageException("something else"), request)

		assertEquals(500, response.statusCode.value())
	}
}
