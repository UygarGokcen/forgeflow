package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "quote_line_items")
class QuoteLineItem(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "quote_id", nullable = false)
	var quoteId: UUID,

	@Column(name = "product_id", nullable = false)
	var productId: UUID,

	@Column(nullable = false, precision = 19, scale = 4)
	var quantity: BigDecimal,

	@Column(precision = 19, scale = 4)
	var width: BigDecimal? = null,

	@Column(precision = 19, scale = 4)
	var height: BigDecimal? = null,

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
	var unitPrice: BigDecimal,

	@Column(name = "line_total", nullable = false, precision = 19, scale = 4)
	var lineTotal: BigDecimal,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
