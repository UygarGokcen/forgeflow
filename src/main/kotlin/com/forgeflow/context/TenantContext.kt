package com.forgeflow.context

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.UUID

/**
 * Holds the current request's tenant id via Spring's [RequestAttributes] rather than a raw
 * ThreadLocal: attributes are bound to the ServletRequest itself, so they cannot leak into a
 * pooled thread that later serves an unrelated request.
 */
object TenantContext {

	private const val TENANT_ATTRIBUTE = "forgeflow.currentTenantId"

	fun setCurrentTenant(tenantId: UUID) {
		requestAttributes().setAttribute(TENANT_ATTRIBUTE, tenantId, RequestAttributes.SCOPE_REQUEST)
	}

	fun getCurrentTenant(): UUID =
		getCurrentTenantOrNull() ?: error("No tenant context set for the current request")

	fun getCurrentTenantOrNull(): UUID? =
		RequestContextHolder.getRequestAttributes()
			?.getAttribute(TENANT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? UUID

	private fun requestAttributes(): RequestAttributes =
		RequestContextHolder.getRequestAttributes()
			?: error("TenantContext can only be used within an active web request")
}
