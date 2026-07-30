package com.forgeflow.service

import com.forgeflow.domain.Product
import com.forgeflow.domain.QuoteLineItem
import com.forgeflow.domain.StockMovement
import com.forgeflow.domain.StockMovementReason
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.exception.InsufficientStockException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.ProductMaterialRepository
import com.forgeflow.repository.ProductRepository
import com.forgeflow.repository.StockMovementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class InventoryService(
	private val materialRepository: MaterialRepository,
	private val productMaterialRepository: ProductMaterialRepository,
	private val productRepository: ProductRepository,
	private val stockMovementRepository: StockMovementRepository,
) {

	/**
	 * Takes raw material out of stock for every line on a quote that is being converted. If any
	 * material is short, it throws [InsufficientStockException] (409) and no stock is changed.
	 *
	 * This joins the caller's transaction, the same one that saves the order. So an order can never
	 * exist without its material having been taken out, and a shortfall rolls the whole conversion
	 * back. (The Kafka event is the opposite case: it is sent only *after* that transaction
	 * commits.)
	 *
	 * Products without a recipe use no material. A shop may also resell items it buys in, and that
	 * shouldn't block the conversion.
	 */
	@Transactional
	fun consumeForConversion(tenantId: UUID, quoteId: UUID, lineItems: List<QuoteLineItem>) {
		val required = requiredMaterialQuantities(tenantId, lineItems)
		if (required.isEmpty()) return

		// Locks these rows until the transaction ends, so two conversions running at the same time
		// can't both read the same stock level and both think there is enough.
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

		val movements = required.map { (materialId, needed) ->
			val material = materials.getValue(materialId)
			material.stockQuantity = material.stockQuantity.subtract(needed)
			StockMovement(
				tenantId = tenantId,
				materialId = materialId,
				quantityDelta = needed.negate(),
				balanceAfter = material.stockQuantity,
				reason = StockMovementReason.CONSUMPTION,
				referenceId = quoteId,
			)
		}
		materialRepository.saveAll(materials.values)
		stockMovementRepository.saveAll(movements)
	}

	/** Adds up how much of each material is needed, so a material used by two lines is only
	 *  checked once against stock. */
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
	 * How many "units" of a product a quote line represents, for the recipe.
	 *
	 * This looks at the product's unit of measure, not at the pricing strategy attached to it, so
	 * changing a pricing rule can't change how much material we take. An area-priced product counts
	 * per square meter, everything else per piece.
	 */
	/**
	 * Multiplying BigDecimals keeps adding decimal places, so without this a shortfall of 8.25 m²
	 * would be shown to the user as "8.2500000000000000".
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
