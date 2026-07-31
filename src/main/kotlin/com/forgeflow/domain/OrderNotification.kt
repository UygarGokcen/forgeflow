package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class NotificationChannel {
	ORDER_CONFIRMATION,
}

/**
 * A record that a notification for an order was sent — the real consumer for
 * `forgeflow.order-events`. There's no real email/SMS provider wired up behind it, but the row is
 * exactly what a real integration would build on: it's the durable, queryable proof that a given
 * order was actually notified about, not just that a Kafka message was read.
 *
 * The unique constraint on (tenant_id, order_id, channel) is what makes consuming the event safe
 * to repeat. Kafka only guarantees a message is delivered *at least* once — the consumer can be
 * redelivered the same message after a crash between committing this row and committing its
 * offset. Without the constraint, a redelivery would notify the same order twice.
 */
@Entity
@Table(name = "order_notifications")
class OrderNotification(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "order_id", nullable = false)
	var orderId: UUID,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var channel: NotificationChannel,

	@Column(nullable = false)
	var recipient: String,

	@Id
	@GeneratedValue
	var id: UUID? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)
