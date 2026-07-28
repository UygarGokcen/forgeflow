package com.forgeflow.config

import com.forgeflow.domain.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class JwtClaims(
	val userId: UUID,
	val tenantId: UUID,
	val email: String,
	val role: UserRole,
)

@Component
class JwtService(
	@Value("\${forgeflow.jwt.secret}") secret: String,
	@Value("\${forgeflow.jwt.expiration-minutes}") private val expirationMinutes: Long,
) {

	private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

	val expirationSeconds: Long get() = expirationMinutes * 60

	fun generateToken(userId: UUID, tenantId: UUID, email: String, role: UserRole): String {
		val now = Instant.now()
		return Jwts.builder()
			.subject(userId.toString())
			.claim("tenant_id", tenantId.toString())
			.claim("email", email)
			.claim("role", role.name)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
			.signWith(key)
			.compact()
	}

	fun parseClaims(token: String): JwtClaims {
		val jws: Jws<Claims> = Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
		val claims = jws.payload
		return JwtClaims(
			userId = UUID.fromString(claims.subject),
			tenantId = UUID.fromString(claims["tenant_id", String::class.java]),
			email = claims["email", String::class.java],
			role = UserRole.valueOf(claims["role", String::class.java]),
		)
	}
}
