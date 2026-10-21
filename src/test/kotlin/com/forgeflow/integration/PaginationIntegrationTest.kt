package com.forgeflow.integration

import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.ProductResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Checks the paging wiring the unit tests can't: that `?page`/`?size`/`?sort` reach the
 * repository, the default newest-first order, the page size cap, and that a bad sort property is
 * a 400 rather than a 500.
 */
class PaginationIntegrationTest : AbstractIntegrationTest() {

	@Test
	fun `list endpoints are paged, newest first by default, and capped in size`() {
		val tenant = registerTenant("paging")
		val auth = "Bearer ${tenant.token}"

		for (i in 1..3) {
			mockMvc.perform(
				post("/api/v1/products")
					.header("Authorization", auth)
					.contentType(MediaType.APPLICATION_JSON)
					.content(
						objectMapper.writeValueAsString(
							CreateProductRequest(
								sku = "SKU-PAGE-$i",
								name = "Product $i",
								description = null,
								baseUnitPrice = BigDecimal("5.00"),
								unitOfMeasure = UnitOfMeasure.PIECE,
							),
						),
					),
			).andExpect(status().isCreated)
		}

		val firstPageJson = mockMvc.perform(get("/api/v1/products?size=2").header("Authorization", auth))
			.andExpect(status().isOk).andReturn().response.contentAsString
		val firstPage = objectMapper.readTree(firstPageJson)
		assertEquals(3, firstPage["totalElements"].asInt())
		assertEquals(2, firstPage["totalPages"].asInt())
		val firstPageSkus = pageContent(firstPageJson, Array<ProductResponse>::class.java).map { it.sku }
		assertEquals(listOf("SKU-PAGE-3", "SKU-PAGE-2"), firstPageSkus)

		val secondPageJson = mockMvc.perform(get("/api/v1/products?size=2&page=1").header("Authorization", auth))
			.andExpect(status().isOk).andReturn().response.contentAsString
		assertEquals(listOf("SKU-PAGE-1"), pageContent(secondPageJson, Array<ProductResponse>::class.java).map { it.sku })

		val sortedJson = mockMvc.perform(get("/api/v1/products?sort=sku,asc").header("Authorization", auth))
			.andExpect(status().isOk).andReturn().response.contentAsString
		assertEquals(
			listOf("SKU-PAGE-1", "SKU-PAGE-2", "SKU-PAGE-3"),
			pageContent(sortedJson, Array<ProductResponse>::class.java).map { it.sku },
		)

		val hugePageJson = mockMvc.perform(get("/api/v1/products?size=100000").header("Authorization", auth))
			.andExpect(status().isOk).andReturn().response.contentAsString
		assertEquals(100, objectMapper.readTree(hugePageJson)["size"].asInt())

		mockMvc.perform(get("/api/v1/products?sort=doesNotExist").header("Authorization", auth))
			.andExpect(status().isBadRequest)
	}
}
