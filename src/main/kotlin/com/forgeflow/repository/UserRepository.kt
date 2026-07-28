package com.forgeflow.repository

import com.forgeflow.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
	@Transactional(readOnly = true)
	fun findByTenantIdAndEmail(tenantId: UUID, email: String): User?

	@Transactional(readOnly = true)
	fun existsByTenantIdAndEmail(tenantId: UUID, email: String): Boolean
}
