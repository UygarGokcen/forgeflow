package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant
import java.util.UUID

/**
 * An event waiting to be sent to Kafka, written in the same transaction as the change that
 * caused it. See V9__add_outbox_events.sql for why this exists.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEvent(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "event_type", nullable = false)
	var eventType: String,

	/**
	 * The event as JSON text. Kept as a plain string and cast to jsonb on write, so the exact
	 * JSON produced by the app's ObjectMapper is stored and read back unchanged (amounts keep
	 * their full BigDecimal precision instead of passing through a double).
	 */
	@ColumnTransformer(write = "?::jsonb")
	@Column(nullable = false, columnDefinition = "jsonb")
	var payload: String,

	@Column(nullable = false)
	var attempts: Int = 0,

	@Column(name = "last_error")
	var lastError: String? = null,

	@Column(name = "published_at")
	var publishedAt: Instant? = null,

	@Id
	@GeneratedValue
	var id: UUID? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)
