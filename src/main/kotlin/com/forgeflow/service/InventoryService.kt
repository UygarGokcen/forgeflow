package com.forgeflow.service

import com.forgeflow.domain.Product
import com.forgeflow.domain.QuoteLineItem
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.exception.InsufficientStockException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.ProductMaterialRepository
import com.forgeflow.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class InventoryService(
	private val materialRepository: MaterialRepository,
	private val productMaterialRepository: ProductMaterialRepository,
	private val productRepository: ProductRepository,
) {

	/**
	 * Draws down raw material for every line on a quote being converted to an order, or throws
	 * [InsufficientStockException] (409) and leaves stock untouched if any material falls short.
	 *
	 * Deliberately has no `@Transactional` of its own beyond joining the caller's: it runs inside
	 * the same transaction that writes the Order, so an order can never exist without its material
	 * having been consumed, and a shortfall rolls the whole conversion back. (Contrast with the
	 * Kafka event, which is intentionally deferred until *after* that transaction commits.)
	 *
	 * Products with no recipe consume nothing — a shop may well sell bought-in items it doesn't
	 * manufacture, and that shouldn't block a conversion.
	 */
	@Transactional
	fun consumeForConversion(tenantId: UUID, lineItems: List<QuoteLineItem>) {
		val required = requiredMaterialQuantities(tenantId, lineItems)
		if (required.isEmpty()) return

		// Locks the rows for the rest of this transaction so two conversions can't both read the
		// same stock level and each decide there's enough.
		val materials = materialRepository.lockAllByTenantIdAndIdIn(tenantId, required.keys.sorted())
			.associateBy { it.id!! }

		val shortfalls = required.mapNotNull { (materialId, needed) ->
			val material = materials[materialId] ?: return@mapNotNull "material $materialId no longer exists"
			if (material.stockQuantity < needed) {
				"${material.sku} needs ${needed.tidy()} ${material.unitOfMeasure} " +
					"but only ${material.stockQuantity.tidy()} in stock"
			} else {
				null
			}
		}
		if (shortfalls.isNotEmpty()) throw InsufficientStockException(shortfalls)

		required.forEach { (materialId, needed) ->
			val material = materials.getValue(materialId)
			material.stockQuantity = material.stockQuantity.subtract(needed)
		}
		materialRepository.saveAll(materials.values)
	}

	/** Total draw per material across all lines, so a material used by two lines is checked once. */
	private fun requiredMaterialQuantities(
		tenantId: UUID,
		lineItems: List<QuoteLineItem>,
	): Map<UUID, BigDecimal> {
		if (lineItems.isEmpty()) return emptyMap()

		val productIds = lineItems.map { it.productId }.distinct()
		val productsById = productRepository.findAllByTenantIdAndIdIn(tenantId, productIds).associateBy { it.id!! }
		val recipesByProduct = productMaterialRepository
			.findAllByTenantIdAndProductIdIn(tenantId, productIds)
			.groupBy { it.productId }

		val totals = mutableMapOf<UUID, BigDecimal>()
		for (line in lineItems) {
			val recipe = recipesByProduct[line.productId].orEmpty()
			if (recipe.isEmpty()) continue

			val product = productsById[line.productId] ?: continue
			val units = consumableUnits(product, line)
			for (entry in recipe) {
				val draw = entry.quantityPerUnit.multiply(units)
				totals.merge(entry.materialId, draw, BigDecimal::add)
			}
		}
		return totals
	}

	/**
	 * How many "units" of a product a quote line represents, for recipe purposes. Keyed off the
	 * product's unit of measure rather than whichever pricing strategy happens to be attached, so
	 * material draw stays correct even if pricing rules change: an area-priced product consumes per
	 * square meter, everything else per piece.
	 */
	/**
	 * Chained BigDecimal multiplication accumulates scale, so a shortfall of 8.25 m² would otherwise
	 * be reported to the user as "8.2500000000000000".
	 */
	private fun BigDecimal.tidy(): String = stripTrailingZeros().toPlainString()

	private fun consumableUnits(product: Product, line: QuoteLineItem): BigDecimal {
		val width = line.width
		val height = line.height
		return if (product.unitOfMeasure == UnitOfMeasure.SQUARE_METER && width != null && height != null) {
			width.multiply(height).multiply(line.quantity)
		} else {
			line.quantity
		}
	}
}
