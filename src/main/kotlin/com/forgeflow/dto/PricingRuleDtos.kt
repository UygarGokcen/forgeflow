package com.forgeflow.dto

import com.forgeflow.domain.PricingStrategyType
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreatePricingRuleRequest(
	@field:NotNull
	val strategyType: PricingStrategyType,

	@field:NotEmpty
	val config: Map<String, Any>,

	val priority: Int = 0,
)

data class PricingRuleResponse(
	val id: UUID,
	val productId: UUID,
	val strategyType: PricingStrategyType,
	val config: Map<String, Any>,
	val priority: Int,
	val isActive: Boolean,
	val createdAt: Instant,
	val updatedAt: Instant,
)
