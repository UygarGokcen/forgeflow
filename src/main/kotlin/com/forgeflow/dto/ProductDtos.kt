package com.forgeflow.dto

import com.forgeflow.domain.UnitOfMeasure
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
	@field:NotBlank
	@field:Size(max = 100)
	val sku: String,

	@field:NotBlank
	@field:Size(max = 255)
	val name: String,

	@field:Size(max = 2000)
	val description: String? = null,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val baseUnitPrice: BigDecimal,

	@field:NotNull
	val unitOfMeasure: UnitOfMeasure,
)

data class UpdateProductRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val name: String,

	@field:Size(max = 2000)
	val description: String? = null,

	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val baseUnitPrice: BigDecimal,

	@field:NotNull
	val unitOfMeasure: UnitOfMeasure,

	@field:NotNull
	val isActive: Boolean,
)

data class ProductResponse(
	val id: UUID,
	val sku: String,
	val name: String,
	val description: String?,
	val baseUnitPrice: BigDecimal,
	val unitOfMeasure: UnitOfMeasure,
	val isActive: Boolean,
	val createdAt: Instant,
	val updatedAt: Instant,
)
