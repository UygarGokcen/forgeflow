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

enum class UnitOfMeasure {
	PIECE,
	SQUARE_METER,
	LINEAR_METER,
	KILOGRAM,
}

@Entity
@Table(name = "products")
class Product(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(nullable = false)
	var sku: String,

	@Column(nullable = false)
	var name: String,

	@Column
	var description: String? = null,

	@Column(name = "base_unit_price", nullable = false, precision = 19, scale = 4)
	var baseUnitPrice: BigDecimal,

	@Enumerated(EnumType.STRING)
	@Column(name = "unit_of_measure", nullable = false)
	var unitOfMeasure: UnitOfMeasure,

	@Column(name = "is_active", nullable = false)
	var isActive: Boolean = true,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
