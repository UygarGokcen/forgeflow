package com.forgeflow.service

import com.forgeflow.config.JwtService
import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Tenant
import com.forgeflow.domain.User
import com.forgeflow.domain.UserRole
import com.forgeflow.dto.AuthResponse
import com.forgeflow.dto.LoginRequest
import com.forgeflow.dto.RegisterTenantRequest
import com.forgeflow.exception.InvalidCredentialsException
import com.forgeflow.exception.TenantAlreadyExistsException
import com.forgeflow.exception.UserAlreadyExistsException
import com.forgeflow.repository.TenantRepository
import com.forgeflow.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Intentionally NOT annotated `@Transactional` at the class/method level: `tenants` is unprotected
 * by RLS, but `users` is. The tenant lookup/insert and the user lookup/insert must run as separate
 * repository-managed transactions so [TenantContext] can be set *between* them — if both ran inside
 * one outer transaction, [com.forgeflow.config.TenantAwareJpaTransactionManager] would have already
 * opened it (with no tenant bound yet) before this method's body ever gets to resolve the tenant.
 */
@Service
class AuthService(
	private val tenantRepository: TenantRepository,
	private val userRepository: UserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val jwtService: JwtService,
) {

	fun registerTenant(request: RegisterTenantRequest): AuthResponse {
		if (tenantRepository.existsBySlug(request.tenantSlug)) {
			throw TenantAlreadyExistsException(request.tenantSlug)
		}

		val tenant = tenantRepository.save(Tenant(name = request.tenantName, slug = request.tenantSlug))
		TenantContext.setCurrentTenant(tenant.id!!)

		if (userRepository.existsByTenantIdAndEmail(tenant.id!!, request.adminEmail)) {
			throw UserAlreadyExistsException(request.adminEmail)
		}

		val user = userRepository.save(
			User(
				tenantId = tenant.id!!,
				email = request.adminEmail,
				passwordHash = passwordEncoder.encode(request.adminPassword),
				fullName = request.adminFullName,
				role = UserRole.ADMIN,
			),
		)

		return issueToken(tenant, user)
	}

	fun login(request: LoginRequest): AuthResponse {
		val tenant = tenantRepository.findBySlug(request.tenantSlug) ?: throw InvalidCredentialsException()
		TenantContext.setCurrentTenant(tenant.id!!)

		val user = userRepository.findByTenantIdAndEmail(tenant.id!!, request.email)
			?: throw InvalidCredentialsException()

		if (!passwordEncoder.matches(request.password, user.passwordHash)) {
			throw InvalidCredentialsException()
		}

		return issueToken(tenant, user)
	}

	private fun issueToken(tenant: Tenant, user: User): AuthResponse {
		val token = jwtService.generateToken(
			userId = user.id!!,
			tenantId = tenant.id!!,
			email = user.email,
			role = user.role,
		)
		return AuthResponse(
			token = token,
			expiresInSeconds = jwtService.expirationSeconds,
			tenantId = tenant.id!!,
			userId = user.id!!,
			email = user.email,
			role = user.role,
		)
	}
}
