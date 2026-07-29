package com.forgeflow.event

import com.forgeflow.config.ORDER_EVENTS_TOPIC
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Placeholder for a consumer this project doesn't have yet, like notifications, fulfillment or
 * billing. It only logs, but it proves the events can actually be read back and aren't just being
 * written to a topic nobody listens to. A real consumer would live in its own service.
 */
@Component
class OrderEventListener {

	private val log = LoggerFactory.getLogger(OrderEventListener::class.java)

	@KafkaListener(topics = [ORDER_EVENTS_TOPIC], groupId = "forgeflow-backend")
	fun onOrderConverted(event: OrderConvertedEvent) {
		log.info(
			"[order-events] order {} ({}) confirmed for tenant {} — total {}",
			event.orderNumber,
			event.orderId,
			event.tenantId,
			event.totalAmount,
		)
	}
}
