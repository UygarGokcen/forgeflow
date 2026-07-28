package com.forgeflow.service

import com.forgeflow.domain.Order
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.OrderRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class OrderServiceTest {

	private val orderRepository: OrderRepository = mock()
	private val orderService = OrderService(orderRepository)
	private val tenantId: UUID = UUID.randomUUID()

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	@Test
	fun `get throws for an order belonging to another tenant`() {
		val id = UUID.randomUUID()
		whenever(orderRepository.findByTenantIdAndId(tenantId, id)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) {
			orderService.get(id)
		}
	}

	@Test
	fun `get returns the order scoped to the current tenant`() {
		val order = Order(
			tenantId = tenantId,
			quoteId = UUID.randomUUID(),
			orderNumber = "ORD-20260101-0001",
			customerName = "Contoso",
			totalAmount = BigDecimal("60.0000"),
			createdBy = UUID.randomUUID(),
			id = UUID.randomUUID(),
		)
		whenever(orderRepository.findByTenantIdAndId(tenantId, order.id!!)).thenReturn(order)

		val response = orderService.get(order.id!!)

		assertEquals("ORD-20260101-0001", response.orderNumber)
		assertEquals(BigDecimal("60.0000"), response.totalAmount)
	}
}
