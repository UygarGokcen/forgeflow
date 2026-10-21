package com.forgeflow.repository

import com.forgeflow.domain.PurchaseOrderLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface PurchaseOrderLineItemRepository : JpaRepository<PurchaseOrderLineItem, UUID> {

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndPurchaseOrderId(tenantId: UUID, purchaseOrderId: UUID): List<PurchaseOrderLineItem>

	/** Line items for a whole page of purchase orders in one query. */
	@Transactional(readOnly = true)
	fun findAllByTenantIdAndPurchaseOrderIdIn(tenantId: UUID, purchaseOrderIds: Collection<UUID>): List<PurchaseOrderLineItem>
}
