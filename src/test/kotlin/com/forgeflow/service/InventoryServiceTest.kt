package com.forgeflow.service

import com.forgeflow.domain.Material
import com.forgeflow.domain.Product
import com.forgeflow.domain.ProductMaterial
import com.forgeflow.domain.QuoteLineItem
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.exception.InsufficientStockException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.ProductMaterialRepository
import com.forgeflow.repository.ProductRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class InventoryServiceTest {

	private val materialRepository: MaterialRepository = mock()
	private val productMaterialRepository: ProductMaterialRepository = mock()
	private val productRepository: ProductRepository = mock()

	private val inventoryService = InventoryService(
		materialRepository,
		productMaterialRepository,
		productRepository,
	)

	private val tenantId: UUID = UUID.randomUUID()

	/**
	 * Compares by value rather than [BigDecimal.equals], which also compares scale — 96.7 and
	 * 96.700 are the same amount of steel, and which one comes out depends on incidental scale
	 * arithmetic in the multiplications.
	 */
	private fun assertQuantity(expected: String, actual: BigDecimal) =
		assertTrue(
			BigDecimal(expected).compareTo(actual) == 0,
			"expected quantity $expected but was $actual",
		)

	@Test
	fun `an area-priced product draws material per square meter, not per piece`() {
		val panel = product("PANEL-01", UnitOfMeasure.SQUARE_METER)
		// 1.1 m2 of sheet per m2 of panel — a waste/offcut allowance.
		val sheet = material("SHEET-01", stock = BigDecimal("100"))
		stubRecipe(panel, sheet, quantityPerUnit = BigDecimal("1.1"))

		// 2.0m x 1.5m = 3 m2, one of them.
		val line = lineItem(panel, quantity = BigDecimal("1"), width = BigDecimal("2.0"), height = BigDecimal("1.5"))

		inventoryService.consumeForConversion(tenantId, listOf(line))

		// 3 m2 * 1.1 = 3.3 drawn from 100.
		assertQuantity("96.7", sheet.stockQuantity)
	}

	@Test
	fun `a piece-priced product draws material per piece`() {
		val bolt = product("BOLT-01", UnitOfMeasure.PIECE)
		val steel = material("STEEL-01", stock = BigDecimal("100"))
		stubRecipe(bolt, steel, quantityPerUnit = BigDecimal("0.25"))

		val line = lineItem(bolt, quantity = BigDecimal("40"))

		inventoryService.consumeForConversion(tenantId, listOf(line))

		// 40 pieces * 0.25 kg = 10 kg.
		assertQuantity("90", steel.stockQuantity)
	}

	@Test
	fun `draw from two lines sharing a material is summed before the stock check`() {
		val panel = product("PANEL-01", UnitOfMeasure.PIECE)
		val steel = material("STEEL-01", stock = BigDecimal("10"))
		stubRecipe(panel, steel, quantityPerUnit = BigDecimal("1"))

		// Six units total against ten in stock — fine individually and fine combined.
		val lines = listOf(lineItem(panel, BigDecimal("4")), lineItem(panel, BigDecimal("2")))

		inventoryService.consumeForConversion(tenantId, lines)

		assertQuantity("4", steel.stockQuantity)
	}

	@Test
	fun `a shortfall throws and leaves stock untouched`() {
		val panel = product("PANEL-01", UnitOfMeasure.PIECE)
		val steel = material("STEEL-01", stock = BigDecimal("5"))
		stubRecipe(panel, steel, quantityPerUnit = BigDecimal("1"))

		val line = lineItem(panel, quantity = BigDecimal("9"))

		assertThrows(InsufficientStockException::class.java) {
			inventoryService.consumeForConversion(tenantId, listOf(line))
		}

		assertQuantity("5", steel.stockQuantity)
		verify(materialRepository, never()).saveAll(any<List<Material>>())
	}

	@Test
	fun `a product with no recipe consumes nothing`() {
		val boughtIn = product("RESALE-01", UnitOfMeasure.PIECE)
		whenever(productRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(listOf(boughtIn))
		whenever(productMaterialRepository.findAllByTenantIdAndProductIdIn(any(), any())).thenReturn(emptyList())

		inventoryService.consumeForConversion(tenantId, listOf(lineItem(boughtIn, BigDecimal("3"))))

		verify(materialRepository, never()).lockAllByTenantIdAndIdIn(any(), any())
	}

	private fun stubRecipe(product: Product, material: Material, quantityPerUnit: BigDecimal) {
		whenever(productRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(listOf(product))
		whenever(productMaterialRepository.findAllByTenantIdAndProductIdIn(any(), any())).thenReturn(
			listOf(
				ProductMaterial(
					tenantId = tenantId,
					productId = product.id!!,
					materialId = material.id!!,
					quantityPerUnit = quantityPerUnit,
					id = UUID.randomUUID(),
				),
			),
		)
		whenever(materialRepository.lockAllByTenantIdAndIdIn(any(), any())).thenReturn(listOf(material))
	}

	private fun product(sku: String, unitOfMeasure: UnitOfMeasure) = Product(
		tenantId = tenantId,
		sku = sku,
		name = sku,
		baseUnitPrice = BigDecimal("10.00"),
		unitOfMeasure = unitOfMeasure,
		id = UUID.randomUUID(),
	)

	private fun material(sku: String, stock: BigDecimal) = Material(
		tenantId = tenantId,
		sku = sku,
		name = sku,
		unitOfMeasure = UnitOfMeasure.KILOGRAM,
		stockQuantity = stock,
		id = UUID.randomUUID(),
	)

	private fun lineItem(
		product: Product,
		quantity: BigDecimal,
		width: BigDecimal? = null,
		height: BigDecimal? = null,
	) = QuoteLineItem(
		tenantId = tenantId,
		quoteId = UUID.randomUUID(),
		productId = product.id!!,
		quantity = quantity,
		width = width,
		height = height,
		unitPrice = BigDecimal("10.00"),
		lineTotal = BigDecimal("10.00"),
		id = UUID.randomUUID(),
	)
}
