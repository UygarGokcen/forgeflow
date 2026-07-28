package com.forgeflow.service

import com.forgeflow.domain.PricingRule
import com.forgeflow.domain.Product
import com.forgeflow.repository.PricingRuleRepository
import com.forgeflow.repository.ProductRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val PRODUCTS_CACHE = "products"
private const val ACTIVE_PRICING_RULES_CACHE = "activePricingRules"
private const val CACHE_KEY = "#tenantId + ':' + #productId"

/**
 * Caches the two DB lookups [com.forgeflow.service.QuoteService.addLineItem] performs on every
 * single quote line item — the product and its active pricing rules — since neither changes often
 * relative to how frequently quotes get priced. Kept as a small, dedicated facade rather than
 * annotating ProductRepository/PricingRuleRepository methods directly, since Spring's caching proxy
 * and this project's custom `@Transactional`-per-repository-method setup have already proven
 * (during the RLS work) not to compose reliably when stacked implicitly.
 *
 * Tenant id is always part of the cache key explicitly — never inferred — so a caching bug can't
 * become a cross-tenant data leak the way a missing `WHERE tenant_id = ?` could.
 */
@Service
class PricingLookupCache(
	private val productRepository: ProductRepository,
	private val pricingRuleRepository: PricingRuleRepository,
) {

	@Cacheable(cacheNames = [PRODUCTS_CACHE], key = CACHE_KEY)
	@Transactional(readOnly = true)
	fun findProduct(tenantId: UUID, productId: UUID): Product? =
		productRepository.findByTenantIdAndId(tenantId, productId)

	@Cacheable(cacheNames = [ACTIVE_PRICING_RULES_CACHE], key = CACHE_KEY)
	@Transactional(readOnly = true)
	fun findActiveRules(tenantId: UUID, productId: UUID): List<PricingRule> =
		pricingRuleRepository.findAllByTenantIdAndProductIdAndIsActiveTrue(tenantId, productId)

	@CacheEvict(cacheNames = [PRODUCTS_CACHE], key = CACHE_KEY)
	fun evictProduct(tenantId: UUID, productId: UUID) {
		// body intentionally empty — @CacheEvict does the work
	}

	@CacheEvict(cacheNames = [ACTIVE_PRICING_RULES_CACHE], key = CACHE_KEY)
	fun evictActiveRules(tenantId: UUID, productId: UUID) {
		// body intentionally empty — @CacheEvict does the work
	}
}
