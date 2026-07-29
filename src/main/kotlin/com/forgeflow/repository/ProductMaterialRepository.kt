package com.forgeflow.repository

import com.forgeflow.domain.ProductMaterial
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ProductMaterialRepository : JpaRepository<ProductMaterial, UUID> {

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndProductId(tenantId: UUID, productId: UUID): List<ProductMaterial>

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndProductIdIn(tenantId: UUID, productIds: Collection<UUID>): List<ProductMaterial>

	@Transactional(readOnly = true)
	fun findByTenantIdAndProductIdAndId(tenantId: UUID, productId: UUID, id: UUID): ProductMaterial?

	@Transactional(readOnly = true)
	fun existsByTenantIdAndProductIdAndMaterialId(tenantId: UUID, productId: UUID, materialId: UUID): Boolean
}
