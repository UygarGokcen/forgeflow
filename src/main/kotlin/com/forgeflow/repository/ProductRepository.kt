package com.forgeflow.repository

import com.forgeflow.domain.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID> {
	@Transactional(readOnly = true)
	fun findByTenantIdAndId(tenantId: UUID, id: UUID): Product?

	@Transactional(readOnly = true)
	fun findAllByTenantId(tenantId: UUID): List<Product>

	@Transactional(readOnly = true)
	fun existsByTenantIdAndSku(tenantId: UUID, sku: String): Boolean
}
