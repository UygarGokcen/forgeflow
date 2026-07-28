package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * The confirmed record of a [Quote] once it transitions to CONVERTED_TO_ORDER. Line items are
 * intentionally not duplicated here — they're read from quote_line_items via [quoteId], since an
 * order is the confirmed snapshot of a quote rather than an independently editable document.
 */
@Entity
@Table(name = "orders")
class Order(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "quote_id", nullable = false)
	var quoteId: UUID,

	@Column(name = "order_number", nullable = false)
	var orderNumber: String,

	@Column(name = "customer_name", nullable = false)
	var customerName: String,

	@Column(name = "customer_email")
	var customerEmail: String? = null,

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	var totalAmount: BigDecimal,

	@Column(name = "created_by", nullable = false)
	var createdBy: UUID,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
