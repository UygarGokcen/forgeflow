package com.forgeflow.dto

import com.forgeflow.domain.StockMovementReason
import com.forgeflow.domain.UnitOfMeasure
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateMaterialRequest(
	@field:NotBlank
	@field:Size(max = 100)
	val sku: String,

	@field:NotBlank
	@field:Size(max = 255)
	val name: String,

	@field:NotNull
	val unitOfMeasure: UnitOfMeasure,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val stockQuantity: BigDecimal,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val reorderLevel: BigDecimal,
)

/**
 * Stock is deliberately not editable here. It only ever changes through a recorded
 * [StockMovementReason] — see [AdjustStockRequest] — so there is always a ledger entry explaining
 * why the number is what it is.
 */
data class UpdateMaterialRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val name: String,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val reorderLevel: BigDecimal,
)

data class AdjustStockRequest(
	@field:NotNull
	val quantityDelta: BigDecimal,

	@field:Size(max = 500)
	val note: String? = null,
)

data class StockMovementResponse(
	val id: UUID,
	val materialId: UUID,
	val quantityDelta: BigDecimal,
	val balanceAfter: BigDecimal,
	val reason: StockMovementReason,
	val referenceId: UUID?,
	val note: String?,
	val createdAt: Instant,
)

data class MaterialResponse(
	val id: UUID,
	val sku: String,
	val name: String,
	val unitOfMeasure: UnitOfMeasure,
	val stockQuantity: BigDecimal,
	val reorderLevel: BigDecimal,
	val belowReorderLevel: Boolean,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class AddProductMaterialRequest(
	@field:NotNull
	val materialId: UUID,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = false)
	val quantityPerUnit: BigDecimal,
)

data class ProductMaterialResponse(
	val id: UUID,
	val productId: UUID,
	val materialId: UUID,
	val materialSku: String,
	val materialName: String,
	val materialUnitOfMeasure: UnitOfMeasure,
	val quantityPerUnit: BigDecimal,
)
