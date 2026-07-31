package com.forgeflow.event

import com.forgeflow.config.ORDER_EVENTS_TOPIC
import com.forgeflow.context.TenantContext
import com.forgeflow.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * The consumer for `forgeflow.order-events`. [NotificationService] does the actual work; this
 * class is just the Kafka wiring around it.
 *
 * Unlike an HTTP request, a listener thread has no [com.forgeflow.context.TenantContext] bound —
 * there's no servlet request to hang it on — so [TenantContext.runWithTenant] binds one from the
 * event's own `tenantId` for the duration of this call. Without that, RLS would have no
 * `app.current_tenant` to check and every query [NotificationService] runs would see nothing.
 */
@Component
class OrderEventListener(
	private val notificationService: NotificationService,
) {

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
		TenantContext.runWithTenant(event.tenantId) {
			notificationService.recordOrderConfirmation(event)
		}
	}
}
