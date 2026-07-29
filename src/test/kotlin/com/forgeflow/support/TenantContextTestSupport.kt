package com.forgeflow.support

import com.forgeflow.context.TenantContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * [TenantContext] and [com.forgeflow.context.CurrentUser] read from Spring's
 * `RequestContextHolder` and `SecurityContextHolder`, which normally only exist during a real web
 * request. The service unit tests call the services directly, so this sets up a fake request and
 * security context around each test.
 */
object TenantContextTestSupport {

	fun bind(tenantId: UUID, userId: UUID = UUID.randomUUID()) {
		RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))
		TenantContext.setCurrentTenant(tenantId)
		SecurityContextHolder.getContext().authentication =
			UsernamePasswordAuthenticationToken(userId, null, emptyList())
	}

	fun unbind() {
		RequestContextHolder.resetRequestAttributes()
		SecurityContextHolder.clearContext()
	}
}
