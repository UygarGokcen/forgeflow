package com.forgeflow.context

import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.context.request.RequestContextHolder
import java.util.UUID

class TenantContextTest {

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	@Test
	fun `getCurrentTenant fails outside any bound context`() {
		assertThrows(IllegalStateException::class.java) { TenantContext.getCurrentTenant() }
	}

	@Test
	fun `runWithTenant binds the given tenant for the duration of the block`() {
		val tenantId = UUID.randomUUID()

		val seenInsideBlock = TenantContext.runWithTenant(tenantId) { TenantContext.getCurrentTenant() }

		assertEquals(tenantId, seenInsideBlock)
	}

	@Test
	fun `runWithTenant clears its binding afterwards instead of leaking to the next call on this thread`() {
		TenantContext.runWithTenant(UUID.randomUUID()) { }

		assertNull(TenantContext.getCurrentTenantOrNull())
	}

	@Test
	fun `runWithTenant restores whatever request-scoped tenant was bound before it ran`() {
		val requestTenant = UUID.randomUUID()
		TenantContextTestSupport.bind(requestTenant)

		TenantContext.runWithTenant(UUID.randomUUID()) { }

		assertEquals(requestTenant, TenantContext.getCurrentTenant())
	}

	@Test
	fun `runWithTenant still restores the previous binding when the block throws`() {
		val requestTenant = UUID.randomUUID()
		TenantContextTestSupport.bind(requestTenant)

		assertThrows(RuntimeException::class.java) {
			TenantContext.runWithTenant(UUID.randomUUID()) { throw RuntimeException("boom") }
		}

		assertEquals(requestTenant, TenantContext.getCurrentTenant())
	}

	@Test
	fun `runWithTenant does not leave RequestContextHolder bound when nothing was bound before`() {
		TenantContext.runWithTenant(UUID.randomUUID()) { }

		assertNull(RequestContextHolder.getRequestAttributes())
	}
}
