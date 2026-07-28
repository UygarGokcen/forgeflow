package com.forgeflow.service

import com.forgeflow.config.JwtService
import com.forgeflow.domain.Tenant
import com.forgeflow.domain.User
import com.forgeflow.domain.UserRole
import com.forgeflow.dto.LoginRequest
import com.forgeflow.dto.RegisterTenantRequest
import com.forgeflow.exception.InvalidCredentialsException
import com.forgeflow.exception.TenantAlreadyExistsException
import com.forgeflow.exception.UserAlreadyExistsException
import com.forgeflow.repository.TenantRepository
import com.forgeflow.repository.UserRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

class AuthServiceTest {

	private val tenantRepository: TenantRepository = mock()
	private val userRepository: UserRepository = mock()
	private val passwordEncoder: PasswordEncoder = mock()
	private val jwtService: JwtService = mock()

	private val authService = AuthService(tenantRepository, userRepository, passwordEncoder, jwtService)

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(UUID.randomUUID())

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	@Test
	fun `registerTenant rejects a slug that already exists`() {
		whenever(tenantRepository.existsBySlug("acme")).thenReturn(true)

		assertThrows(TenantAlreadyExistsException::class.java) {
			authService.registerTenant(registerRequest(slug = "acme"))
		}
	}

	@Test
	fun `registerTenant rejects an email that already exists for the new tenant`() {
		val tenant = Tenant(name = "Acme", slug = "acme", id = UUID.randomUUID())
		whenever(tenantRepository.existsBySlug("acme")).thenReturn(false)
		doReturn(tenant).whenever(tenantRepository).save(any())
		whenever(userRepository.existsByTenantIdAndEmail(tenant.id!!, "ada@acme.test")).thenReturn(true)

		assertThrows(UserAlreadyExistsException::class.java) {
			authService.registerTenant(registerRequest(slug = "acme", email = "ada@acme.test"))
		}
	}

	@Test
	fun `registerTenant creates an ADMIN user and issues a token`() {
		val tenant = Tenant(name = "Acme", slug = "acme", id = UUID.randomUUID())
		val userId = UUID.randomUUID()
		whenever(tenantRepository.existsBySlug("acme")).thenReturn(false)
		doReturn(tenant).whenever(tenantRepository).save(any())
		whenever(userRepository.existsByTenantIdAndEmail(tenant.id!!, "ada@acme.test")).thenReturn(false)
		whenever(passwordEncoder.encode("supersecret1")).thenReturn("hashed")
		doAnswer { invocation ->
			(invocation.arguments[0] as User).also { it.id = userId }
		}.whenever(userRepository).save(any())
		whenever(jwtService.generateToken(any(), any(), any(), any())).thenReturn("a.jwt.token")
		whenever(jwtService.expirationSeconds).thenReturn(3600L)

		val response = authService.registerTenant(registerRequest(slug = "acme", email = "ada@acme.test"))

		assertEquals("a.jwt.token", response.token)
		assertEquals(UserRole.ADMIN, response.role)
		verify(jwtService).generateToken(eq(userId), eq(tenant.id!!), eq("ada@acme.test"), eq(UserRole.ADMIN))
	}

	@Test
	fun `login rejects an unknown tenant slug`() {
		whenever(tenantRepository.findBySlug("acme")).thenReturn(null)

		assertThrows(InvalidCredentialsException::class.java) {
			authService.login(LoginRequest("acme", "ada@acme.test", "supersecret1"))
		}
	}

	@Test
	fun `login rejects an unknown email within the tenant`() {
		val tenant = Tenant(name = "Acme", slug = "acme", id = UUID.randomUUID())
		whenever(tenantRepository.findBySlug("acme")).thenReturn(tenant)
		whenever(userRepository.findByTenantIdAndEmail(tenant.id!!, "ghost@acme.test")).thenReturn(null)

		assertThrows(InvalidCredentialsException::class.java) {
			authService.login(LoginRequest("acme", "ghost@acme.test", "supersecret1"))
		}
	}

	@Test
	fun `login rejects a wrong password`() {
		val tenant = Tenant(name = "Acme", slug = "acme", id = UUID.randomUUID())
		val user = User(
			tenantId = tenant.id!!,
			email = "ada@acme.test",
			passwordHash = "hashed",
			fullName = "Ada",
			role = UserRole.ADMIN,
			id = UUID.randomUUID(),
		)
		whenever(tenantRepository.findBySlug("acme")).thenReturn(tenant)
		whenever(userRepository.findByTenantIdAndEmail(tenant.id!!, "ada@acme.test")).thenReturn(user)
		whenever(passwordEncoder.matches("wrong", "hashed")).thenReturn(false)

		assertThrows(InvalidCredentialsException::class.java) {
			authService.login(LoginRequest("acme", "ada@acme.test", "wrong"))
		}
	}

	private fun registerRequest(slug: String, email: String = "ada@acme.test") = RegisterTenantRequest(
		tenantName = "Acme",
		tenantSlug = slug,
		adminFullName = "Ada Admin",
		adminEmail = email,
		adminPassword = "supersecret1",
	)
}
