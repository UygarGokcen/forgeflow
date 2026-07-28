package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

enum class PricingStrategyType {
	VOLUME_DISCOUNT,
	AREA_BASED,
	FIXED,
}

@Entity
@Table(name = "pricing_rules")
class PricingRule(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "product_id", nullable = false)
	var productId: UUID,

	@Enumerated(EnumType.STRING)
	@Column(name = "strategy_type", nullable = false)
	var strategyType: PricingStrategyType,

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	var config: MutableMap<String, Any> = mutableMapOf(),

	@Column(nullable = false)
	var priority: Int = 0,

	@Column(name = "is_active", nullable = false)
	var isActive: Boolean = true,

	@Id
	@GeneratedValue
	var id: UUID? = null,
) : AuditableEntity()
