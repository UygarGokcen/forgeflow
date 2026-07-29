package com.forgeflow.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Raised when a quote is converted into an order. This is a plain Spring application event, not a
 * Kafka message. [OrderEventPublisher] is what sends it to Kafka, and only after the transaction
 * commits.
 *
 * Sending it inside the transaction would risk putting an event on the topic for an order that a
 * later error rolled back.
 */
data class OrderConvertedEvent(
	val orderId: UUID,
	val quoteId: UUID,
	val tenantId: UUID,
	val orderNumber: String,
	val customerName: String,
	val customerEmail: String?,
	val totalAmount: BigDecimal,
	val createdBy: UUID,
	val occurredAt: Instant = Instant.now(),
)
