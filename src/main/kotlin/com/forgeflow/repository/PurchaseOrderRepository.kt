package com.forgeflow.repository

import com.forgeflow.domain.PurchaseOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, UUID> {

	@Transactional(readOnly = true)
	fun findByTenantIdAndId(tenantId: UUID, id: UUID): PurchaseOrder?

	@Transactional(readOnly = true)
	fun findAllByTenantId(tenantId: UUID): List<PurchaseOrder>

	@Transactional(readOnly = true)
	fun existsByTenantIdAndPoNumber(tenantId: UUID, poNumber: String): Boolean
}
