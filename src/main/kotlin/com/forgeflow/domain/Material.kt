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
 * A raw material kept in stock, like steel sheet, profile or coil.
 *
 * This is separate from [Product] on purpose. A custom manufacturer keeps material and cuts the
 * finished product to order, so stock belongs here and not on the product catalogue.
 */
@Entity
@Table(name = "materials")
class Material(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(nullable = false)
	var sku: String,

	@Column(nullable = false)
	var name: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "unit_of_measure", nullable = false)
	var unitOfMeasure: UnitOfMeasure,

	@Column(name = "stock_quantity", nullable = false, precision = 19, scale = 4)
	var stockQuantity: BigDecimal = BigDecimal.ZERO,

	/** When stock drops to this level or below, the material shows up as needing a restock. */
	@Column(name = "reorder_level", nullable = false, precision = 19, scale = 4)
	var reorderLevel: BigDecimal = BigDecimal.ZERO,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
