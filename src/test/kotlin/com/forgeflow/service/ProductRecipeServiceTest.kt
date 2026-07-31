package com.forgeflow.service

import com.forgeflow.domain.Material
import com.forgeflow.domain.Product
import com.forgeflow.domain.ProductMaterial
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.AddProductMaterialRequest
import com.forgeflow.exception.DuplicateRecipeEntryException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.ProductMaterialRepository
import com.forgeflow.repository.ProductRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class ProductRecipeServiceTest {

	private val productMaterialRepository: ProductMaterialRepository = mock()
	private val productRepository: ProductRepository = mock()
	private val materialRepository: MaterialRepository = mock()

	private val productRecipeService = ProductRecipeService(
		productMaterialRepository,
		productRepository,
		materialRepository,
	)

	private val tenantId: UUID = UUID.randomUUID()

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	private fun product() = Product(
		tenantId = tenantId,
		sku = "PANEL-01",
		name = "Panel",
		baseUnitPrice = BigDecimal("10.00"),
		unitOfMeasure = UnitOfMeasure.SQUARE_METER,
		id = UUID.randomUUID(),
	)

	private fun material() = Material(
		tenantId = tenantId,
		sku = "SHEET-01",
		name = "Steel sheet",
		unitOfMeasure = UnitOfMeasure.SQUARE_METER,
		stockQuantity = BigDecimal("100"),
		id = UUID.randomUUID(),
	)

	@Test
	fun `add fails when the product doesn't belong to this tenant`() {
		val productId = UUID.randomUUID()
		whenever(productRepository.findByTenantIdAndId(tenantId, productId)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) {
			productRecipeService.add(productId, AddProductMaterialRequest(UUID.randomUUID(), BigDecimal("1")))
		}
	}

	@Test
	fun `add fails when the material doesn't belong to this tenant`() {
		val product = product()
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		whenever(materialRepository.findByTenantIdAndId(any(), any())).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) {
			productRecipeService.add(product.id!!, AddProductMaterialRequest(UUID.randomUUID(), BigDecimal("1")))
		}
	}

	@Test
	fun `add rejects a material that's already on this product's recipe`() {
		val product = product()
		val material = material()
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		whenever(materialRepository.findByTenantIdAndId(tenantId, material.id!!)).thenReturn(material)
		whenever(
			productMaterialRepository.existsByTenantIdAndProductIdAndMaterialId(tenantId, product.id!!, material.id!!),
		).thenReturn(true)

		assertThrows(DuplicateRecipeEntryException::class.java) {
			productRecipeService.add(product.id!!, AddProductMaterialRequest(material.id!!, BigDecimal("1.1")))
		}
	}

	@Test
	fun `add saves a new recipe entry`() {
		val product = product()
		val material = material()
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		whenever(materialRepository.findByTenantIdAndId(tenantId, material.id!!)).thenReturn(material)
		whenever(
			productMaterialRepository.existsByTenantIdAndProductIdAndMaterialId(tenantId, product.id!!, material.id!!),
		).thenReturn(false)
		doAnswer { (it.arguments[0] as ProductMaterial).also { pm -> pm.id = UUID.randomUUID() } }
			.whenever(productMaterialRepository).save(any())

		val response = productRecipeService.add(product.id!!, AddProductMaterialRequest(material.id!!, BigDecimal("1.1")))

		assertEquals(material.sku, response.materialSku)
		assertTrue(BigDecimal("1.1").compareTo(response.quantityPerUnit) == 0)
	}

	@Test
	fun `list fails when the product doesn't belong to this tenant`() {
		val productId = UUID.randomUUID()
		whenever(productRepository.findByTenantIdAndId(tenantId, productId)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) { productRecipeService.list(productId) }
	}

	@Test
	fun `list joins recipe entries with their material details`() {
		val product = product()
		val material = material()
		val entry = ProductMaterial(
			tenantId = tenantId,
			productId = product.id!!,
			materialId = material.id!!,
			quantityPerUnit = BigDecimal("2"),
			id = UUID.randomUUID(),
		)
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		whenever(productMaterialRepository.findAllByTenantIdAndProductId(tenantId, product.id!!)).thenReturn(listOf(entry))
		whenever(materialRepository.findAllByTenantIdAndIdIn(tenantId, listOf(material.id!!))).thenReturn(listOf(material))

		val response = productRecipeService.list(product.id!!)

		assertEquals(1, response.size)
		assertEquals(material.sku, response.single().materialSku)
	}

	@Test
	fun `remove fails for an entry belonging to another tenant or product`() {
		val productId = UUID.randomUUID()
		val entryId = UUID.randomUUID()
		whenever(productMaterialRepository.findByTenantIdAndProductIdAndId(tenantId, productId, entryId)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) { productRecipeService.remove(productId, entryId) }
	}

	@Test
	fun `remove deletes an owned entry`() {
		val product = product()
		val entry = ProductMaterial(
			tenantId = tenantId,
			productId = product.id!!,
			materialId = UUID.randomUUID(),
			quantityPerUnit = BigDecimal("1"),
			id = UUID.randomUUID(),
		)
		whenever(productMaterialRepository.findByTenantIdAndProductIdAndId(tenantId, product.id!!, entry.id!!))
			.thenReturn(entry)

		productRecipeService.remove(product.id!!, entry.id!!)

		verify(productMaterialRepository).delete(entry)
	}
}
