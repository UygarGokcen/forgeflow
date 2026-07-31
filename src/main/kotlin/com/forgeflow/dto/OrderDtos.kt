package com.forgeflow.dto

import com.forgeflow.domain.NotificationChannel
import com.forgeflow.domain.OrderStatus
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderResponse(
	val id: UUID,
	val quoteId: UUID,
	val orderNumber: String,
	val customerName: String,
	val customerEmail: String?,
	val totalAmount: BigDecimal,
	val status: OrderStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class UpdateOrderStatusRequest(
	@field:NotNull
	val status: OrderStatus,
)

data class OrderNotificationResponse(
	val id: UUID,
	val orderId: UUID,
	val channel: NotificationChannel,
	val recipient: String,
	val createdAt: Instant,
)
