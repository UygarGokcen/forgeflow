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

/**
 * Where an order is in the shop, after the quote that produced it has already been paid for /
 * committed to. This is a separate lifecycle from [QuoteStatus] on purpose: a quote's status is
 * about whether the customer has agreed to buy, an order's status is about whether the shop has
 * built and shipped what was agreed to.
 */
enum class OrderStatus {
	CONFIRMED,
	IN_PRODUCTION,
	SHIPPED,
	DELIVERED,
	CANCELLED,
}

/**
 * The confirmed record of a [Quote] after it moves to CONVERTED_TO_ORDER.
 *
 * The line items are not copied here. They are read from quote_line_items using [quoteId], because
 * an order is a snapshot of the quote rather than a separate document you can edit.
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: OrderStatus = OrderStatus.CONFIRMED,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
