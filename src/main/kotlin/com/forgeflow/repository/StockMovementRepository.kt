package com.forgeflow.repository

import com.forgeflow.domain.StockMovement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface StockMovementRepository : JpaRepository<StockMovement, UUID> {

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndMaterialIdOrderByCreatedAtDesc(tenantId: UUID, materialId: UUID): List<StockMovement>
}
