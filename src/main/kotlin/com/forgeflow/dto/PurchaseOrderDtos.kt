package com.forgeflow.dto

import com.forgeflow.domain.PurchaseOrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreatePurchaseOrderLineItemRequest(
	@field:NotNull
	val materialId: UUID,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = false)
	val quantity: BigDecimal,
)

data class CreatePurchaseOrderRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val supplierName: String,

	@field:NotEmpty
	@field:Valid
	val lineItems: List<CreatePurchaseOrderLineItemRequest>,
)

data class UpdatePurchaseOrderStatusRequest(
	@field:NotNull
	val status: PurchaseOrderStatus,
)

data class PurchaseOrderLineItemResponse(
	val id: UUID,
	val materialId: UUID,
	val materialSku: String,
	val quantityOrdered: BigDecimal,
)

data class PurchaseOrderResponse(
	val id: UUID,
	val poNumber: String,
	val supplierName: String,
	val status: PurchaseOrderStatus,
	val lineItems: List<PurchaseOrderLineItemResponse>,
	val createdAt: Instant,
	val updatedAt: Instant,
)
