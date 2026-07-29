package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * One line of a product's recipe: how much of a given [Material] a single unit of the product
 * consumes. "Unit" means a piece for piece-priced products and one square meter for area-priced
 * ones — the same dimension basis area-based pricing uses, so a quote line's price and its
 * material draw are derived from the same numbers.
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
