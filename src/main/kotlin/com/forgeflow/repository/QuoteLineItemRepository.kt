package com.forgeflow.repository

import com.forgeflow.domain.QuoteLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface QuoteLineItemRepository : JpaRepository<QuoteLineItem, UUID> {
	@Transactional(readOnly = true)
	fun findAllByTenantIdAndQuoteId(tenantId: UUID, quoteId: UUID): List<QuoteLineItem>

	/** Line items for a whole page of quotes in one query, instead of one query per quote. */
	@Transactional(readOnly = true)
	fun findAllByTenantIdAndQuoteIdIn(tenantId: UUID, quoteIds: Collection<UUID>): List<QuoteLineItem>

	@Transactional(readOnly = true)
	fun findByTenantIdAndQuoteIdAndId(tenantId: UUID, quoteId: UUID, id: UUID): QuoteLineItem?
}
