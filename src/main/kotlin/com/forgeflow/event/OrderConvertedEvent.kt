package com.forgeflow.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Raised when a quote is converted into an order. This is a plain Spring application event, not a
 * Kafka message: [OutboxWriter] stores it in `outbox_events` inside the same transaction as the
 * order, and [OutboxRelay] sends it to Kafka from there.
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
