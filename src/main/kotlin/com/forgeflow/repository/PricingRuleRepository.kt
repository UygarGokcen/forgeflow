package com.forgeflow.repository

import com.forgeflow.domain.PricingRule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PricingRuleRepository : JpaRepository<PricingRule, UUID> {
	@Transactional(readOnly = true)
	fun findAllByTenantIdAndProductIdAndIsActiveTrue(tenantId: UUID, productId: UUID): List<PricingRule>

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndProductId(tenantId: UUID, productId: UUID): List<PricingRule>

	@Transactional(readOnly = true)
	fun findByTenantIdAndProductIdAndId(tenantId: UUID, productId: UUID, id: UUID): PricingRule?
}
