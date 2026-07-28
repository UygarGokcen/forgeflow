package com.forgeflow.dto

import com.forgeflow.domain.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterTenantRequest(
	@field:NotBlank
	@field:Size(max = 255)
	val tenantName: String,

	@field:NotBlank
	@field:Size(max = 100)
	@field:Pattern(regexp = "^[a-z0-9-]+$", message = "slug may only contain lowercase letters, digits and hyphens")
	val tenantSlug: String,

	@field:NotBlank
	@field:Size(max = 255)
	val adminFullName: String,

	@field:NotBlank
	@field:Email
	@field:Size(max = 255)
	val adminEmail: String,

	@field:NotBlank
	@field:Size(min = 8, max = 100)
	val adminPassword: String,
)

data class LoginRequest(
	@field:NotBlank
	val tenantSlug: String,

	@field:NotBlank
	@field:Email
	val email: String,

	@field:NotBlank
	val password: String,
)

data class AuthResponse(
	val token: String,
	val tokenType: String = "Bearer",
	val expiresInSeconds: Long,
	val tenantId: UUID,
	val userId: UUID,
	val email: String,
	val role: UserRole,
)
