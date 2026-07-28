package com.forgeflow.repository

import com.forgeflow.domain.Quote
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface QuoteRepository : JpaRepository<Quote, UUID> {
	@Transactional(readOnly = true)
	fun findByTenantIdAndId(tenantId: UUID, id: UUID): Quote?

	@Transactional(readOnly = true)
	fun findAllByTenantId(tenantId: UUID): List<Quote>

	@Transactional(readOnly = true)
	fun existsByTenantIdAndQuoteNumber(tenantId: UUID, quoteNumber: String): Boolean
}
