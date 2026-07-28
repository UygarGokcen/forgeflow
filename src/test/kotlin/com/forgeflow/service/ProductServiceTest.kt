package com.forgeflow.service

import com.forgeflow.domain.Product
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.UpdateProductRequest
import com.forgeflow.exception.DuplicateSkuException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.ProductRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class ProductServiceTest {

	private val productRepository: ProductRepository = mock()
	private val productService = ProductService(productRepository)
	private val tenantId: UUID = UUID.randomUUID()

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	@Test
	fun `create rejects a duplicate sku within the tenant`() {
		whenever(productRepository.existsByTenantIdAndSku(tenantId, "SKU-1")).thenReturn(true)

		assertThrows(DuplicateSkuException::class.java) {
			productService.create(createRequest("SKU-1"))
		}
	}

	@Test
	fun `create saves a product scoped to the current tenant`() {
		whenever(productRepository.existsByTenantIdAndSku(tenantId, "SKU-1")).thenReturn(false)
		doAnswer { (it.arguments[0] as Product).also { p -> p.id = UUID.randomUUID() } }
			.whenever(productRepository).save(any())

		val response = productService.create(createRequest("SKU-1"))

		assertEquals("SKU-1", response.sku)
		assertEquals(BigDecimal("10.00"), response.baseUnitPrice)
	}

	@Test
	fun `get throws when the product does not belong to the current tenant`() {
		val id = UUID.randomUUID()
		whenever(productRepository.findByTenantIdAndId(tenantId, id)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) {
			productService.get(id)
		}
	}

	@Test
	fun `update mutates the existing entity and flushes`() {
		val product = product(sku = "SKU-1", name = "Old Name")
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		doAnswer { it.arguments[0] as Product }.whenever(productRepository).saveAndFlush(any())

		val response = productService.update(
			product.id!!,
			UpdateProductRequest(
				name = "New Name",
				description = "updated",
				baseUnitPrice = BigDecimal("15.00"),
				unitOfMeasure = UnitOfMeasure.PIECE,
				isActive = false,
			),
		)

		assertEquals("New Name", response.name)
		assertEquals(BigDecimal("15.00"), response.baseUnitPrice)
		assertEquals(false, response.isActive)
	}

	@Test
	fun `delete throws for a product owned by another tenant`() {
		val id = UUID.randomUUID()
		whenever(productRepository.findByTenantIdAndId(tenantId, id)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) {
			productService.delete(id)
		}
	}

	private fun createRequest(sku: String) = CreateProductRequest(
		sku = sku,
		name = "Steel Panel",
		description = null,
		baseUnitPrice = BigDecimal("10.00"),
		unitOfMeasure = UnitOfMeasure.SQUARE_METER,
	)

	private fun product(sku: String, name: String) = Product(
		tenantId = tenantId,
		sku = sku,
		name = name,
		baseUnitPrice = BigDecimal("10.00"),
		unitOfMeasure = UnitOfMeasure.SQUARE_METER,
		id = UUID.randomUUID(),
	)
}
