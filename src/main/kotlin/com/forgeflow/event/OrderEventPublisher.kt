package com.forgeflow.event

import com.forgeflow.config.ORDER_EVENTS_TOPIC
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventPublisher(
	private val kafkaTemplate: KafkaTemplate<String, OrderConvertedEvent>,
) {

	private val log = LoggerFactory.getLogger(OrderEventPublisher::class.java)

	/**
	 * Runs only after the transaction that created the order has committed, and not at all if it
	 * rolled back.
	 *
	 * Sending is fire-and-forget. If Kafka is down we log it instead of throwing, because the
	 * conversion is already saved in Postgres and a broker problem shouldn't undo it.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun onOrderConverted(event: OrderConvertedEvent) {
		kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.tenantId.toString(), event)
			.whenComplete { result, ex ->
				if (ex != null) {
					log.error("Failed to publish order-converted event for order {}", event.orderId, ex)
				} else {
					log.info(
						"Published order-converted event for order {} to partition {}",
						event.orderId,
						result.recordMetadata.partition(),
					)
				}
			}
	}
}
