package com.forgeflow.config

import com.forgeflow.domain.UserRole
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

private const val TEST_SECRET = "unit-test-only-secret-value-must-be-at-least-256-bits-long"

class JwtServiceTest {

	@Test
	fun `round-trips all claims through generate and parse`() {
		val jwtService = JwtService(TEST_SECRET, expirationMinutes = 60)
		val userId = UUID.randomUUID()
		val tenantId = UUID.randomUUID()

		val token = jwtService.generateToken(userId, tenantId, "ada@acme.test", UserRole.ADMIN)
		val claims = jwtService.parseClaims(token)

		assertEquals(userId, claims.userId)
		assertEquals(tenantId, claims.tenantId)
		assertEquals("ada@acme.test", claims.email)
		assertEquals(UserRole.ADMIN, claims.role)
	}

	@Test
	fun `rejects an expired token`() {
		val jwtService = JwtService(TEST_SECRET, expirationMinutes = -1)
		val token = jwtService.generateToken(UUID.randomUUID(), UUID.randomUUID(), "x@x.test", UserRole.SALES_REP)

		assertThrows(JwtException::class.java) {
			jwtService.parseClaims(token)
		}
	}

	@Test
	fun `rejects a token signed with a different secret`() {
		val issuer = JwtService(TEST_SECRET, expirationMinutes = 60)
		val verifier = JwtService("a-completely-different-secret-value-also-256-bits-minimum-long", 60)
		val token = issuer.generateToken(UUID.randomUUID(), UUID.randomUUID(), "x@x.test", UserRole.ADMIN)

		assertThrows(JwtException::class.java) {
			verifier.parseClaims(token)
		}
	}
}
