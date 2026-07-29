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
 * Caches the two lookups that [com.forgeflow.service.QuoteService.addLineItem] does for every quote
 * line: the product and its active pricing rules. Neither changes often, but quotes are priced a
 * lot, so this saves repeated database reads.
 *
 * It is a small separate class instead of `@Cacheable` on the repositories, because Spring's cache
 * proxy and the per-method `@Transactional` this project needs for RLS don't stack together
 * reliably.
 *
 * The tenant id is always written into the cache key by hand. That way a caching mistake can't
 * turn into one tenant reading another tenant's data.
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
		// Empty on purpose: @CacheEvict does the work
	}

	@CacheEvict(cacheNames = [ACTIVE_PRICING_RULES_CACHE], key = CACHE_KEY)
	fun evictActiveRules(tenantId: UUID, productId: UUID) {
		// Empty on purpose: @CacheEvict does the work
	}
}
