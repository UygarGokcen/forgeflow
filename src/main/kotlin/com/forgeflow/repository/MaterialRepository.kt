package com.forgeflow.repository

import com.forgeflow.domain.Material
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface MaterialRepository : JpaRepository<Material, UUID> {

	@Transactional(readOnly = true)
	fun findByTenantIdAndId(tenantId: UUID, id: UUID): Material?

	@Transactional(readOnly = true)
	fun findAllByTenantId(tenantId: UUID): List<Material>

	@Transactional(readOnly = true)
	fun existsByTenantIdAndSku(tenantId: UUID, sku: String): Boolean

	@Transactional(readOnly = true)
	fun findAllByTenantIdAndIdIn(tenantId: UUID, ids: Collection<UUID>): List<Material>

	@Transactional(readOnly = true)
	@Query("select m from Material m where m.tenantId = :tenantId and m.stockQuantity <= m.reorderLevel")
	fun findLowStock(@Param("tenantId") tenantId: UUID): List<Material>

	/**
	 * Locks the given materials for the remainder of the calling transaction (`SELECT ... FOR
	 * UPDATE`), so two quotes converting at the same time can't both read the same stock level and
	 * each conclude there's enough. Deliberately *not* `readOnly`: it joins the caller's write
	 * transaction (the one creating the Order), which is what holds the lock until that commits.
	 *
	 * The `order by` is not cosmetic — it makes concurrent callers acquire row locks in the same
	 * sequence, which is what stops two overlapping conversions from deadlocking each other.
	 */
	@Transactional
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Material m where m.tenantId = :tenantId and m.id in :ids order by m.id")
	fun lockAllByTenantIdAndIdIn(
		@Param("tenantId") tenantId: UUID,
		@Param("ids") ids: Collection<UUID>,
	): List<Material>
}
