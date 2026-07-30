package com.forgeflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class StockMovementReason {
	/** Stock a material started with when it was first created. */
	INITIAL_STOCK,

	/** A person correcting or topping up stock by hand, for example after a delivery or a count. */
	MANUAL_ADJUSTMENT,

	/** Stock drawn automatically when a quote converts to an order. */
	CONSUMPTION,
}

/**
 * One entry in the append-only ledger of everything that has happened to a material's stock.
 *
 * `materials.stock_quantity` is a running total, which is fast to read but throws away history —
 * there is no way to answer "why is this number what it is" or rebuild it after a mistake. This
 * table exists to answer that. It is insert-only: nothing here is ever updated or deleted, so a
 * row written once is a permanent record of what happened, when.
 */
@Entity
@Table(name = "stock_movements")
class StockMovement(

	@Column(name = "tenant_id", nullable = false)
	var tenantId: UUID,

	@Column(name = "material_id", nullable = false)
	var materialId: UUID,

	/** Positive for stock coming in, negative for stock going out. Never zero. */
	@Column(name = "quantity_delta", nullable = false, precision = 19, scale = 4)
	var quantityDelta: BigDecimal,

	/** The material's stock quantity right after this movement was applied. */
	@Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
	var balanceAfter: BigDecimal,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var reason: StockMovementReason,

	/** The quote that triggered a [StockMovementReason.CONSUMPTION] draw. Null for other reasons. */
	@Column(name = "reference_id")
	var referenceId: UUID? = null,

	@Column(name = "note")
	var note: String? = null,

	@Id
	@GeneratedValue
	var id: UUID? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant = Instant.now(),
)
