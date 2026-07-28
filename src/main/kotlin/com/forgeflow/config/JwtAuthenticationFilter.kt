package com.forgeflow.config

import com.forgeflow.context.TenantContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val TENANT_HEADER = "X-Tenant-ID"

@Component
class JwtAuthenticationFilter(
	private val jwtService: JwtService,
	private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val authHeader = request.getHeader("Authorization")
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response)
			return
		}

		val token = authHeader.removePrefix("Bearer ").trim()
		val claims = try {
			jwtService.parseClaims(token)
		} catch (ex: JwtException) {
			writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token")
			return
		} catch (ex: IllegalArgumentException) {
			writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Malformed token")
			return
		}

		val headerTenantId = request.getHeader(TENANT_HEADER)
		if (headerTenantId != null) {
			val parsedHeaderTenantId = runCatching { UUID.fromString(headerTenantId) }.getOrNull()
			if (parsedHeaderTenantId == null || parsedHeaderTenantId != claims.tenantId) {
				writeError(response, HttpServletResponse.SC_FORBIDDEN, "X-Tenant-ID does not match the authenticated tenant")
				return
			}
		}

		TenantContext.setCurrentTenant(claims.tenantId)

		val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}"))
		val authentication = UsernamePasswordAuthenticationToken(claims.userId, null, authorities)
		SecurityContextHolder.getContext().authentication = authentication

		filterChain.doFilter(request, response)
	}

	private fun writeError(response: HttpServletResponse, status: Int, message: String) {
		response.status = status
		response.contentType = "application/json"
		response.writer.write(objectMapper.writeValueAsString(mapOf("status" to status, "message" to message)))
	}
}
