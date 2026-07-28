package com.forgeflow.repository

import com.forgeflow.domain.QuoteLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface QuoteLineItemRepository : JpaRepository<QuoteLineItem, UUID> {
	@Transactional(readOnly = true)
	fun findAllByTenantIdAndQuoteId(tenantId: UUID, quoteId: UUID): List<QuoteLineItem>

	@Transactional(readOnly = true)
	fun findByTenantIdAndQuoteIdAndId(tenantId: UUID, quoteId: UUID, id: UUID): QuoteLineItem?
}
