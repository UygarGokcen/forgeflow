package com.forgeflow.dto

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
	val createdAt: Instant,
	val updatedAt: Instant,
)
