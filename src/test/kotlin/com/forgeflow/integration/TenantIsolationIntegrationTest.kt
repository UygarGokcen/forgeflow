package com.forgeflow.integration

import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.ProductResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Checks the row-level-security tenant isolation this project depends on.
 *
 * It runs against a real Postgres container, not mocks, and connects as the same `forgeflow_app`
 * role the app uses in production. That is what makes it useful: if RLS stopped working, this test
 * would fail, while a test with mocked repositories would still pass.
 */
class TenantIsolationIntegrationTest : AbstractIntegrationTest() {

	@Test
	fun `a tenant cannot read another tenant's product by id`() {
		val tenantA = registerTenant("iso-a")
		val tenantB = registerTenant("iso-b")

		val productJson = mockMvc.perform(
			post("/api/v1/products")
				.header("Authorization", "Bearer ${tenantA.token}")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreateProductRequest(
							sku = "SKU-ISO-1",
							name = "Isolation Test Product",
							description = null,
							baseUnitPrice = BigDecimal("10.00"),
							unitOfMeasure = UnitOfMeasure.PIECE,
						),
					),
				),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val product = objectMapper.readValue(productJson, ProductResponse::class.java)

		// Tenant B must not even learn the product exists.
		mockMvc.perform(
			get("/api/v1/products/${product.id}").header("Authorization", "Bearer ${tenantB.token}"),
		).andExpect(status().isNotFound)

		// Tenant A can still see its own product.
		mockMvc.perform(
			get("/api/v1/products/${product.id}").header("Authorization", "Bearer ${tenantA.token}"),
		).andExpect(status().isOk)
	}

	@Test
	fun `product listing never leaks rows across tenants`() {
		val tenantA = registerTenant("list-a")
		val tenantB = registerTenant("list-b")

		repeat(3) { i ->
			mockMvc.perform(
				post("/api/v1/products")
					.header("Authorization", "Bearer ${tenantA.token}")
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsString(
							CreateProductRequest(
								sku = "SKU-LIST-$i",
								name = "Product $i",
								description = null,
								baseUnitPrice = BigDecimal("5.00"),
								unitOfMeasure = UnitOfMeasure.PIECE,
							),
						),
					),
			).andExpect(status().isCreated)
		}

		val tenantBListJson = mockMvc.perform(
			get("/api/v1/products").header("Authorization", "Bearer ${tenantB.token}"),
		).andExpect(status().isOk).andReturn().response.contentAsString
		val tenantBProducts = objectMapper.readValue(tenantBListJson, Array<ProductResponse>::class.java)

		assertTrue(tenantBProducts.isEmpty(), "tenant B must not see tenant A's products")
	}

	@Test
	fun `requests without a token are rejected`() {
		mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized)
	}
}
