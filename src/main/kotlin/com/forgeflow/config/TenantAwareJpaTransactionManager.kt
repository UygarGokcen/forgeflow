package com.forgeflow.config

import com.forgeflow.context.TenantContext
import jakarta.persistence.EntityManagerFactory
import org.hibernate.Session
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Sets the current tenant on the Postgres connection so the row-level-security policies
 * (`current_setting('app.current_tenant')`) can do their job.
 *
 * This has to happen in [doBegin], right after the transaction opens and before any query runs.
 * `set_config(..., true)` works like `SET LOCAL`: it only lasts for the current transaction. If we
 * set it later, a query could already have run without a tenant.
 *
 * Every repository query method needs its own `@Transactional` for this to work. Spring Data's
 * default handling for derived query methods doesn't reliably use this custom transaction manager,
 * and then the tenant is never set at all.
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
