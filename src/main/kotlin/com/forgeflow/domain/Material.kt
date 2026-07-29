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
 * A raw material held in stock (steel sheet, profile, coil). Distinct from [Product]: a custom
 * manufacturer stocks material and cuts finished goods to order, so stock is tracked here rather
 * than on the product catalogue.
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

	/** Stock at or below this level is reported as needing replenishment. */
	@Column(name = "reorder_level", nullable = false, precision = 19, scale = 4)
	var reorderLevel: BigDecimal = BigDecimal.ZERO,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
