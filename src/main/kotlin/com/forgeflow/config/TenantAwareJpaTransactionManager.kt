package com.forgeflow.config

import com.forgeflow.context.TenantContext
import jakarta.persistence.EntityManagerFactory
import org.hibernate.Session
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Binds the current [TenantContext] tenant id to the Postgres session on every transaction so
 * row-level-security policies (`current_setting('app.current_tenant')`) can enforce isolation.
 *
 * This must run inside [doBegin], right after the physical connection/transaction is opened and
 * before any repository code executes on that connection — `SET LOCAL`/`set_config(..., true)`
 * only takes effect for the remainder of the *current* transaction, so setting it any later (e.g.
 * via an interceptor around the service method) risks a query slipping through before the tenant
 * is bound.
 *
 * Every repository query method that should participate in this binding MUST carry an explicit
 * `@Transactional` — Spring Data's implicit default transactional wrapping for derived query
 * methods does not reliably route through this custom transaction manager, which would silently
 * skip the tenant bind and leave the query exposed to whatever stale `app.current_tenant` value a
 * pooled connection happens to carry over from an unrelated prior transaction.
 */
class TenantAwareJpaTransactionManager(
	entityManagerFactory: EntityManagerFactory,
) : JpaTransactionManager(entityManagerFactory) {

	override fun doBegin(transaction: Any, definition: TransactionDefinition) {
		super.doBegin(transaction, definition)

		val tenantId = TenantContext.getCurrentTenantOrNull() ?: return
		val holder = TransactionSynchronizationManager.getResource(entityManagerFactory!!) as? EntityManagerHolder
			?: return

		val session = holder.entityManager.unwrap(Session::class.java)
		session.doWork { connection ->
			connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)").use { statement ->
				statement.setString(1, tenantId.toString())
				statement.execute()
			}
		}
	}
}
