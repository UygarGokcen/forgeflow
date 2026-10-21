package com.forgeflow.context

import org.slf4j.MDC
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Logging MDC key for the tenant a log line belongs to. */
const val MDC_TENANT_ID = "tenantId"

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

	/**
	 * Runs [block] with [tenantId] bound, for code that isn't handling an HTTP request — a Kafka
	 * listener thread, a scheduled job. Those threads have no [RequestAttributes] to hang the
	 * tenant id on, so this binds a minimal one for the duration of the call and always restores
	 * whatever was there before in a `finally`. Listener threads are pooled and reused for the
	 * next message, so leaving this bound after the call would repeat the exact ThreadLocal leak
	 * this class was built to avoid on the request path — the binding has to be undone explicitly
	 * here instead of relying on a servlet container to do it.
	 */
	fun <T> runWithTenant(tenantId: UUID, block: () -> T): T {
		val previous = RequestContextHolder.getRequestAttributes()
		val previousMdcTenant = MDC.get(MDC_TENANT_ID)
		RequestContextHolder.setRequestAttributes(BackgroundRequestAttributes())
		setCurrentTenant(tenantId)
		// Same tenant label on log lines as the request path gets from JwtAuthenticationFilter.
		MDC.put(MDC_TENANT_ID, tenantId.toString())
		try {
			return block()
		} finally {
			if (previous != null) RequestContextHolder.setRequestAttributes(previous) else RequestContextHolder.resetRequestAttributes()
			if (previousMdcTenant != null) MDC.put(MDC_TENANT_ID, previousMdcTenant) else MDC.remove(MDC_TENANT_ID)
		}
	}

	private fun requestAttributes(): RequestAttributes =
		RequestContextHolder.getRequestAttributes()
			?: error("TenantContext can only be used within an active web request, or inside runWithTenant")
}

/** A bare-bones [RequestAttributes] for [TenantContext.runWithTenant] — just enough attribute
 *  storage for the tenant id, with no servlet request behind it. */
private class BackgroundRequestAttributes : RequestAttributes {
	private val attributes = ConcurrentHashMap<String, Any>()

	override fun getAttribute(name: String, scope: Int): Any? = attributes[name]
	override fun setAttribute(name: String, value: Any, scope: Int) {
		attributes[name] = value
	}
	override fun removeAttribute(name: String, scope: Int) {
		attributes.remove(name)
	}
	override fun getAttributeNames(scope: Int): Array<String> = attributes.keys.toTypedArray()
	override fun registerDestructionCallback(name: String, callback: Runnable, scope: Int) = Unit
	override fun resolveReference(key: String): Any? = null
	override fun getSessionId(): String = "background"
	override fun getSessionMutex(): Any = this
}
