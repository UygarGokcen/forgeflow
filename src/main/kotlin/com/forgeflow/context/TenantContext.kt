package com.forgeflow.context

import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.UUID

/**
 * Holds the tenant id for the current request.
 *
 * This uses Spring's [RequestAttributes] instead of a plain ThreadLocal, because the attributes
 * belong to the request itself. With a ThreadLocal, a pooled thread could still be holding one
 * request's tenant when it picks up the next request.
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
