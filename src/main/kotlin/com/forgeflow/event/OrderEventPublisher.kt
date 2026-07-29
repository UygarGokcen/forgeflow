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
	 * Runs after the DB transaction that created the Order has committed — never before, and never
	 * at all if that transaction rolled back. Publishing is fire-and-forget: a Kafka outage
	 * shouldn't be able to fail (or roll back) a quote conversion that's already durably recorded
	 * in Postgres, so failures are logged rather than propagated.
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
