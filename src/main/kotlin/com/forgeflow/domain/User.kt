package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

enum class UserRole {
	ADMIN,
	SALES_REP,
}

@Entity
@Table(name = "users")
class User(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(nullable = false)
	var email: String,

	@Column(name = "password_hash", nullable = false)
	var passwordHash: String,

	@Column(name = "full_name", nullable = false)
	var fullName: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var role: UserRole,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
