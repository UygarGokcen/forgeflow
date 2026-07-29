package com.forgeflow.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Published (as a plain Spring [org.springframework.context.ApplicationEvent]-style POJO, not a
 * Kafka message directly) when a Quote converts to an Order. [com.forgeflow.event.OrderEventPublisher]
 * relays it to Kafka only after the surrounding transaction commits — publishing eagerly inside the
 * transaction risks a "phantom" event on the topic for an Order that a later failure rolled back.
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
