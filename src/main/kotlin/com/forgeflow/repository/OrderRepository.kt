package com.forgeflow.repository

import com.forgeflow.domain.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
	@Transactional(readOnly = true)
	fun findByTenantIdAndId(tenantId: UUID, id: UUID): Order?

	@Transactional(readOnly = true)
	fun findAllByTenantId(tenantId: UUID): List<Order>

	@Transactional(readOnly = true)
	fun existsByTenantIdAndOrderNumber(tenantId: UUID, orderNumber: String): Boolean
}
