package com.forgeflow.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.forgeflow.config.ORDER_EVENTS_TOPIC
import com.forgeflow.context.MDC_TENANT_ID
import com.forgeflow.repository.OutboxEventRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Gives up on an event after this many failed sends; it stays in the table to be looked at. */
const val OUTBOX_MAX_ATTEMPTS = 10

// Longer than the producer's max.block.ms (10s), so when the broker is unreachable it's Kafka's
// own descriptive error that gets recorded, not a bare TimeoutException from this wait.
private const val SEND_TIMEOUT_SECONDS = 15L

/**
 * Sends pending `outbox_events` rows to Kafka and marks them published.
 *
 * Delivery is at-least-once: if the app dies after Kafka accepted a message but before this
 * transaction commits, the row is still unpublished and gets sent again. That is fine because the
 * consumer, [com.forgeflow.service.NotificationService], already ignores events it has seen.
 */
@Component
class OutboxRelay(
	private val outboxPublisher: OutboxPublisher,
) {

	@Scheduled(fixedDelayString = "\${forgeflow.outbox.poll-interval-ms:1000}")
	fun relay() {
		outboxPublisher.publishPending()
	}
}

/**
 * The transactional half of [OutboxRelay]. Kept as its own bean so the `@Transactional` goes
 * through a Spring proxy instead of depending on how `@Scheduled` happens to invoke the method.
 */
@Component
class OutboxPublisher(
	private val outboxEventRepository: OutboxEventRepository,
	private val kafkaTemplate: KafkaTemplate<String, OrderConvertedEvent>,
	private val objectMapper: ObjectMapper,
	meterRegistry: MeterRegistry,
	@Value("\${forgeflow.outbox.batch-size:50}") private val batchSize: Int,
) {

	private val log = LoggerFactory.getLogger(OutboxPublisher::class.java)

	private val publishedCounter = Counter.builder("forgeflow.outbox.published")
		.description("Outbox events Kafka acknowledged")
		.register(meterRegistry)
	private val failedCounter = Counter.builder("forgeflow.outbox.send.failures")
		.description("Failed attempts to send an outbox event to Kafka")
		.register(meterRegistry)

	/**
	 * One failed event doesn't stop the rest of the batch: it gets its attempt counted and error
	 * recorded, and is picked up again on the next poll.
	 */
	@Transactional
	fun publishPending(): Int {
		val pending = outboxEventRepository.lockNextPending(batchSize, OUTBOX_MAX_ATTEMPTS)
		var published = 0
		for (event in pending) {
			// Label this event's log lines with its tenant, like a request's would be.
			MDC.putCloseable(MDC_TENANT_ID, event.tenantId.toString()).use {
				try {
					val payload = objectMapper.readValue(event.payload, OrderConvertedEvent::class.java)
					// Waiting for the broker's ack is the point: the row is only marked published
					// once Kafka has actually taken the message.
					kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.tenantId.toString(), payload)
						.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
					event.publishedAt = Instant.now()
					publishedCounter.increment()
					published++
				} catch (ex: Exception) {
					event.attempts += 1
					event.lastError = (ex.cause?.message ?: ex.message ?: ex.javaClass.simpleName).take(1000)
					failedCounter.increment()
					log.warn(
						"Failed to publish outbox event {} (attempt {} of {})",
						event.id,
						event.attempts,
						OUTBOX_MAX_ATTEMPTS,
						ex,
					)
				}
			}
		}
		return published
	}
}
