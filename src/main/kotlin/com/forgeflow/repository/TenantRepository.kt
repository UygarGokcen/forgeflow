package com.forgeflow.repository

import com.forgeflow.domain.Tenant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface TenantRepository : JpaRepository<Tenant, UUID> {
	@Transactional(readOnly = true)
	fun findBySlug(slug: String): Tenant?

	@Transactional(readOnly = true)
	fun existsBySlug(slug: String): Boolean
}
