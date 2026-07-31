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

enum class PurchaseOrderStatus {
	/** Being put together. Line items can only be trusted as final once it's submitted. */
	DRAFT,

	/** Sent to the supplier, waiting on delivery. */
	SUBMITTED,

	/** Delivery arrived. Stock has been added and this can no longer change. */
	RECEIVED,

	CANCELLED,
}

/**
 * A restock order placed with a supplier for one or more materials.
 *
 * This is what turns `materials/low-stock` from a report into something that actually closes the
 * loop: marking one `RECEIVED` is what adds the material back to stock, through the same
 * [StockMovement] ledger a quote conversion draws from.
 */
@Entity
@Table(name = "purchase_orders")
class PurchaseOrder(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "po_number", nullable = false)
	var poNumber: String,

	@Column(name = "supplier_name", nullable = false)
	var supplierName: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT,

	@Column(name = "created_by", nullable = false)
	var createdBy: UUID,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()

/** How much of one material was ordered on a [PurchaseOrder]. */
@Entity
@Table(name = "purchase_order_line_items")
class PurchaseOrderLineItem(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "purchase_order_id", nullable = false)
	var purchaseOrderId: UUID,

	@Column(name = "material_id", nullable = false)
	var materialId: UUID,

	@Column(name = "quantity_ordered", nullable = false, precision = 19, scale = 4)
	var quantityOrdered: BigDecimal,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
