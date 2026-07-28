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
 * Regression test for the row-level-security tenant isolation this project relies on. Runs against
 * a real Postgres container (not mocks), connected as the same unprivileged `forgeflow_app` role
 * the production app uses — so this would actually fail if RLS silently stopped being enforced,
 * unlike a unit test against mocked repositories.
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
