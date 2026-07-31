package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.NotificationChannel
import com.forgeflow.domain.OrderNotification
import com.forgeflow.dto.OrderNotificationResponse
import com.forgeflow.event.OrderConvertedEvent
import com.forgeflow.repository.OrderNotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The real consumer for order-converted events. There's no email/SMS provider behind it — it logs
 * where a real send would go — but the [OrderNotification] row it writes is the durable, queryable
 * proof that an order was actually notified about, which is the part a fake "just log it" consumer
 * can't give you.
 */
@Service
class NotificationService(
	private val orderNotificationRepository: OrderNotificationRepository,
) {

	private val log = LoggerFactory.getLogger(NotificationService::class.java)

	/**
	 * Kafka only guarantees a message is delivered *at least* once: if the consumer crashes after
	 * this method commits but before its offset is committed, the same event is redelivered. The
	 * `existsBy` check makes a redelivery a cheap no-op in the common case, and the unique
	 * constraint on (tenant_id, order_id, channel) is the last line of defense if two redeliveries
	 * race each other past that check — same idea as `stock_quantity >= 0` backing up
	 * [InventoryService], or RLS backing up the tenant filter in application code.
	 */
	@Transactional
	fun recordOrderConfirmation(event: OrderConvertedEvent) {
		val channel = NotificationChannel.ORDER_CONFIRMATION
		if (orderNotificationRepository.existsByTenantIdAndOrderIdAndChannel(event.tenantId, event.orderId, channel)) {
			log.info("Order {} was already notified on {}, skipping redelivery", event.orderId, channel)
			return
		}

		val recipient = event.customerEmail ?: "ops@forgeflow.internal"
		try {
			orderNotificationRepository.save(
				OrderNotification(
					tenantId = event.tenantId,
					orderId = event.orderId,
					channel = channel,
					recipient = recipient,
				),
			)
		} catch (ex: DataIntegrityViolationException) {
			log.info("Order {} was notified concurrently by another delivery, skipping", event.orderId)
			return
		}

		log.info(
			"[notification] order {} confirmed to {} — total {}",
			event.orderNumber,
			recipient,
			event.totalAmount,
		)
	}

	@Transactional(readOnly = true)
	fun listForOrder(orderId: UUID): List<OrderNotificationResponse> {
		val tenantId = TenantContext.getCurrentTenant()
		return orderNotificationRepository.findAllByTenantIdAndOrderId(tenantId, orderId).map {
			OrderNotificationResponse(
				id = it.id!!,
				orderId = it.orderId,
				channel = it.channel,
				recipient = it.recipient,
				createdAt = it.createdAt,
			)
		}
	}
}
