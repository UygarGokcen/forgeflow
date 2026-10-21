package com.forgeflow.event

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.forgeflow.config.ORDER_EVENTS_TOPIC
import com.forgeflow.domain.OutboxEvent
import com.forgeflow.repository.OutboxEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

class OutboxTest {

	private val objectMapper = jacksonObjectMapper()
		.registerModule(JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

	private val outboxEventRepository: OutboxEventRepository = mock()
	private val kafkaTemplate: KafkaTemplate<String, OrderConvertedEvent> = mock()

	private val writer = OutboxWriter(outboxEventRepository, objectMapper)
	private val publisher = OutboxPublisher(outboxEventRepository, kafkaTemplate, objectMapper, batchSize = 50)

	private val tenantId: UUID = UUID.randomUUID()

	private fun event() = OrderConvertedEvent(
		orderId = UUID.randomUUID(),
		quoteId = UUID.randomUUID(),
		tenantId = tenantId,
		orderNumber = "ORD-20260101-0001",
		customerName = "Contoso",
		customerEmail = "buyer@contoso.test",
		totalAmount = BigDecimal("60.0000"),
		createdBy = UUID.randomUUID(),
	)

	private fun pending(event: OrderConvertedEvent) = OutboxEvent(
		tenantId = event.tenantId,
		eventType = ORDER_CONVERTED_EVENT_TYPE,
		payload = objectMapper.writeValueAsString(event),
		id = UUID.randomUUID(),
	)

	@Test
	fun `the writer stores the event as JSON instead of sending it`() {
		val event = event()

		writer.onOrderConverted(event)

		val captor = argumentCaptor<OutboxEvent>()
		verify(outboxEventRepository).save(captor.capture())
		assertEquals(tenantId, captor.firstValue.tenantId)
		assertEquals(event, objectMapper.readValue(captor.firstValue.payload, OrderConvertedEvent::class.java))
		verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any<OrderConvertedEvent>())
	}

	@Test
	fun `a stored amount reads back with its full precision`() {
		val stored = pending(event())

		val readBack = objectMapper.readValue(stored.payload, OrderConvertedEvent::class.java)

		assertEquals(BigDecimal("60.0000"), readBack.totalAmount)
	}

	@Test
	fun `an event Kafka accepts is marked published`() {
		val event = event()
		val row = pending(event)
		whenever(outboxEventRepository.lockNextPending(any(), any())).thenReturn(listOf(row))
		whenever(kafkaTemplate.send(eq(ORDER_EVENTS_TOPIC), eq(tenantId.toString()), any<OrderConvertedEvent>()))
			.thenReturn(CompletableFuture.completedFuture(mock<SendResult<String, OrderConvertedEvent>>()))

		val published = publisher.publishPending()

		assertEquals(1, published)
		assertNotNull(row.publishedAt)
		assertEquals(0, row.attempts)
	}

	@Test
	fun `an event Kafka rejects stays pending with the attempt and error recorded`() {
		val row = pending(event())
		whenever(outboxEventRepository.lockNextPending(any(), any())).thenReturn(listOf(row))
		whenever(kafkaTemplate.send(any<String>(), any<String>(), any<OrderConvertedEvent>()))
			.thenReturn(CompletableFuture.failedFuture(RuntimeException("broker unavailable")))

		val published = publisher.publishPending()

		assertEquals(0, published)
		assertNull(row.publishedAt)
		assertEquals(1, row.attempts)
		assertEquals("broker unavailable", row.lastError)
	}

	@Test
	fun `one failing event doesn't stop the rest of the batch`() {
		val failing = pending(event())
		val healthy = pending(event())
		whenever(outboxEventRepository.lockNextPending(any(), any())).thenReturn(listOf(failing, healthy))
		whenever(kafkaTemplate.send(any<String>(), any<String>(), any<OrderConvertedEvent>()))
			.thenReturn(CompletableFuture.failedFuture(RuntimeException("broker unavailable")))
			.thenReturn(CompletableFuture.completedFuture(mock<SendResult<String, OrderConvertedEvent>>()))

		val published = publisher.publishPending()

		assertEquals(1, published)
		assertNull(failing.publishedAt)
		assertNotNull(healthy.publishedAt)
	}
}
