package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.PricingRule
import com.forgeflow.dto.CreatePricingRuleRequest
import com.forgeflow.dto.PricingRuleResponse
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.PricingRuleRepository
import com.forgeflow.repository.ProductRepository
import com.forgeflow.service.pricing.PricingContext
import com.forgeflow.service.pricing.PricingStrategyResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class PricingRuleService(
	private val pricingRuleRepository: PricingRuleRepository,
	private val productRepository: ProductRepository,
	private val strategyResolver: PricingStrategyResolver,
	private val pricingLookupCache: PricingLookupCache,
) {

	@Transactional
	fun create(productId: UUID, request: CreatePricingRuleRequest): PricingRuleResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val product = productRepository.findByTenantIdAndId(tenantId, productId)
			?: throw ResourceNotFoundException("Product $productId not found")

		// Fail fast with a clear 400 at rule-creation time rather than letting a malformed config
		// surface later as an opaque error while pricing an actual quote line.
		strategyResolver.resolve(request.strategyType).calculateLineTotal(
			PricingContext(
				baseUnitPrice = product.baseUnitPrice,
				quantity = BigDecimal.ONE,
				width = BigDecimal.ONE,
				height = BigDecimal.ONE,
				config = request.config,
			),
		)

		val rule = pricingRuleRepository.save(
			PricingRule(
				tenantId = tenantId,
				productId = productId,
				strategyType = request.strategyType,
				config = request.config.toMutableMap(),
				priority = request.priority,
			),
		)
		pricingLookupCache.evictActiveRules(tenantId, productId)
		return rule.toResponse()
	}

	@Transactional(readOnly = true)
	fun list(productId: UUID): List<PricingRuleResponse> {
		val tenantId = TenantContext.getCurrentTenant()
		ensureProductExists(tenantId, productId)
		return pricingRuleRepository.findAllByTenantIdAndProductId(tenantId, productId).map { it.toResponse() }
	}

	@Transactional
	fun delete(productId: UUID, ruleId: UUID) {
		val tenantId = TenantContext.getCurrentTenant()
		val rule = pricingRuleRepository.findByTenantIdAndProductIdAndId(tenantId, productId, ruleId)
			?: throw ResourceNotFoundException("Pricing rule $ruleId not found for product $productId")
		pricingRuleRepository.delete(rule)
		pricingLookupCache.evictActiveRules(tenantId, productId)
	}

	private fun ensureProductExists(tenantId: UUID, productId: UUID) {
		if (productRepository.findByTenantIdAndId(tenantId, productId) == null) {
			throw ResourceNotFoundException("Product $productId not found")
		}
	}

	private fun PricingRule.toResponse() = PricingRuleResponse(
		id = id!!,
		productId = productId,
		strategyType = strategyType,
		config = config,
		priority = priority,
		isActive = isActive,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}
