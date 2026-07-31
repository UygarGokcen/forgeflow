package com.forgeflow.service

import com.forgeflow.domain.NotificationChannel
import com.forgeflow.event.OrderConvertedEvent
import com.forgeflow.repository.OrderNotificationRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.util.UUID

class NotificationServiceTest {

	private val orderNotificationRepository: OrderNotificationRepository = mock()
	private val notificationService = NotificationService(orderNotificationRepository)

	private val tenantId: UUID = UUID.randomUUID()

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	private fun event(customerEmail: String? = "buyer@contoso.com") = OrderConvertedEvent(
		orderId = UUID.randomUUID(),
		quoteId = UUID.randomUUID(),
		tenantId = tenantId,
		orderNumber = "ORD-20260101-0001",
		customerName = "Contoso",
		customerEmail = customerEmail,
		totalAmount = BigDecimal("60.00"),
		createdBy = UUID.randomUUID(),
	)

	@Test
	fun `a new order writes a notification addressed to the customer`() {
		val event = event()
		whenever(
			orderNotificationRepository.existsByTenantIdAndOrderIdAndChannel(
				tenantId,
				event.orderId,
				NotificationChannel.ORDER_CONFIRMATION,
			),
		).thenReturn(false)

		notificationService.recordOrderConfirmation(event)

		val captor = argumentCaptor<com.forgeflow.domain.OrderNotification>()
		verify(orderNotificationRepository).save(captor.capture())
		assertEquals("buyer@contoso.com", captor.firstValue.recipient)
		assertEquals(NotificationChannel.ORDER_CONFIRMATION, captor.firstValue.channel)
	}

	@Test
	fun `an order with no customer email falls back to an internal ops address`() {
		val event = event(customerEmail = null)
		whenever(orderNotificationRepository.existsByTenantIdAndOrderIdAndChannel(any(), any(), any())).thenReturn(false)

		notificationService.recordOrderConfirmation(event)

		val captor = argumentCaptor<com.forgeflow.domain.OrderNotification>()
		verify(orderNotificationRepository).save(captor.capture())
		assertEquals("ops@forgeflow.internal", captor.firstValue.recipient)
	}

	@Test
	fun `a redelivered event that already has a notification on record is skipped`() {
		val event = event()
		whenever(orderNotificationRepository.existsByTenantIdAndOrderIdAndChannel(any(), any(), any())).thenReturn(true)

		notificationService.recordOrderConfirmation(event)

		verify(orderNotificationRepository, never()).save(any())
	}

	@Test
	fun `a unique-constraint violation from a racing redelivery is swallowed, not thrown`() {
		val event = event()
		whenever(orderNotificationRepository.existsByTenantIdAndOrderIdAndChannel(any(), any(), any())).thenReturn(false)
		doThrow(DataIntegrityViolationException("duplicate key")).whenever(orderNotificationRepository).save(any())

		notificationService.recordOrderConfirmation(event)
	}
}
