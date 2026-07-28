package com.forgeflow.dto

import com.forgeflow.domain.QuoteStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateQuoteRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val customerName: String,

	@field:Email
	@field:Size(max = 255)
	val customerEmail: String? = null,
)

data class AddQuoteLineItemRequest(
	@field:NotNull
	val productId: UUID,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = false)
	val quantity: BigDecimal,

	@field:DecimalMin(value = "0", inclusive = false)
	val width: BigDecimal? = null,

	@field:DecimalMin(value = "0", inclusive = false)
	val height: BigDecimal? = null,
)

data class UpdateQuoteStatusRequest(
	@field:NotNull
	val status: QuoteStatus,
)

data class QuoteLineItemResponse(
	val id: UUID,
	val productId: UUID,
	val quantity: BigDecimal,
	val width: BigDecimal?,
	val height: BigDecimal?,
	val unitPrice: BigDecimal,
	val lineTotal: BigDecimal,
)

data class QuoteResponse(
	val id: UUID,
	val quoteNumber: String,
	val customerName: String,
	val customerEmail: String?,
	val status: QuoteStatus,
	val totalAmount: BigDecimal,
	val lineItems: List<QuoteLineItemResponse>,
	val createdAt: Instant,
	val updatedAt: Instant,
)
