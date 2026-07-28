package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

enum class QuoteStatus {
	DRAFT,
	APPROVED,
	CONVERTED_TO_ORDER,
	REJECTED,
}

@Entity
@Table(name = "quotes")
class Quote(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "quote_number", nullable = false)
	var quoteNumber: String,

	@Column(name = "customer_name", nullable = false)
	var customerName: String,

	@Column(name = "customer_email")
	var customerEmail: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: QuoteStatus = QuoteStatus.DRAFT,

	@Column(name = "created_by", nullable = false)
	var createdBy: UUID,

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	var totalAmount: BigDecimal = BigDecimal.ZERO,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
