package com.forgeflow.config

import com.forgeflow.event.OUTBOX_MAX_ATTEMPTS
import com.forgeflow.repository.OutboxEventRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration

/**
 * Gauges for the outbox backlog, read from the table on every scrape.
 *
 * The outbox is where a Kafka problem shows up first: if the broker is down or an event can't be
 * sent, nothing fails loudly, rows just pile up. `forgeflow.outbox.pending` growing is the thing
 * to alert on; `forgeflow.outbox.given_up` above zero means an event needs a person.
 */
@Configuration
class OutboxMetricsConfig(
	outboxEventRepository: OutboxEventRepository,
	meterRegistry: MeterRegistry,
) {
	init {
		Gauge.builder("forgeflow.outbox.pending") { outboxEventRepository.countPending(OUTBOX_MAX_ATTEMPTS).toDouble() }
			.description("Outbox events waiting to be sent to Kafka")
			.register(meterRegistry)
		Gauge.builder("forgeflow.outbox.given_up") { outboxEventRepository.countGivenUp(OUTBOX_MAX_ATTEMPTS).toDouble() }
			.description("Outbox events the relay stopped retrying")
			.register(meterRegistry)
	}
}
