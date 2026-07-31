package com.forgeflow.repository

import com.forgeflow.domain.PurchaseOrderLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PurchaseOrderLineItemRepository : JpaRepository<PurchaseOrderLineItem, UUID> {

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndPurchaseOrderId(tenantId: UUID, purchaseOrderId: UUID): List<PurchaseOrderLineItem>
}
