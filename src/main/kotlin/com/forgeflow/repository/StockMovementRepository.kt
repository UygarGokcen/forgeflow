package com.forgeflow.repository

import com.forgeflow.domain.StockMovement
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface StockMovementRepository : JpaRepository<StockMovement, UUID> {

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndMaterialId(tenantId: UUID, materialId: UUID, pageable: Pageable): Page<StockMovement>
}
