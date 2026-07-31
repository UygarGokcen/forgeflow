package com.forgeflow.repository

import com.forgeflow.domain.NotificationChannel
import com.forgeflow.domain.OrderNotification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface OrderNotificationRepository : JpaRepository<OrderNotification, UUID> {

	@Transactional(readOnly = true)
	fun existsByTenantIdAndOrderIdAndChannel(tenantId: UUID, orderId: UUID, channel: NotificationChannel): Boolean

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndOrderId(tenantId: UUID, orderId: UUID): List<OrderNotification>
}
