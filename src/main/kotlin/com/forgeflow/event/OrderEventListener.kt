package com.forgeflow.event

import com.forgeflow.config.ORDER_EVENTS_TOPIC
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Stand-in for a downstream consumer this platform doesn't have yet (notifications, fulfillment,
 * billing, ...) — demonstrates that events published on conversion are actually consumable, not
 * just written to a topic nothing reads. A real consumer would live in its own service/module.
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
