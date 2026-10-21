package com.forgeflow.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.forgeflow.domain.OutboxEvent
import com.forgeflow.repository.OutboxEventRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

const val ORDER_CONVERTED_EVENT_TYPE = "OrderConverted"

/**
 * Turns an [OrderConvertedEvent] into an `outbox_events` row.
 *
 * This is a plain synchronous [EventListener], so it runs on the thread that published the event
 * and inside its transaction — the one in `QuoteService.updateStatus` that also saves the order.
 * Either both rows commit or neither does. Nothing is sent to Kafka here; [OutboxRelay] does that.
 */
@Component
class OutboxWriter(
	private val outboxEventRepository: OutboxEventRepository,
	private val objectMapper: ObjectMapper,
) {

	@EventListener
	fun onOrderConverted(event: OrderConvertedEvent) {
		outboxEventRepository.save(
			OutboxEvent(
				tenantId = event.tenantId,
				eventType = ORDER_CONVERTED_EVENT_TYPE,
				payload = objectMapper.writeValueAsString(event),
			),
		)
	}
}
