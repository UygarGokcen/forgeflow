package com.forgeflow.dto

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

data class UpdateMaterialRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val name: String,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val stockQuantity: BigDecimal,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val reorderLevel: BigDecimal,
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
