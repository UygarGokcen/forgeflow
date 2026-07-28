package com.forgeflow.service.pricing

import com.forgeflow.domain.PricingStrategyType
import org.springframework.stereotype.Component

@Component
class PricingStrategyResolver(
	strategies: List<PricingStrategy>,
) {
	private val byType: Map<PricingStrategyType, PricingStrategy> = strategies.associateBy { it.type }

	fun resolve(type: PricingStrategyType): PricingStrategy =
		byType[type] ?: error("No PricingStrategy registered for type $type")
}
