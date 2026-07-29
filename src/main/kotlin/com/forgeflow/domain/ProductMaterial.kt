package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * One line of a product's recipe: how much of a [Material] one unit of the product uses.
 *
 * A "unit" is one piece for piece-priced products, and one square meter for area-priced ones.
 * That is the same basis area-based pricing uses, so the price of a quote line and the material it
 * uses are worked out from the same numbers.
 */
@Entity
@Table(name = "product_materials")
class ProductMaterial(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "product_id", nullable = false)
	var productId: UUID,

	@Column(name = "material_id", nullable = false)
	var materialId: UUID,

	@Column(name = "quantity_per_unit", nullable = false, precision = 19, scale = 4)
	var quantityPerUnit: BigDecimal,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
