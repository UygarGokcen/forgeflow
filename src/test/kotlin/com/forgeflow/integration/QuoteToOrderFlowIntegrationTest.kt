package com.forgeflow.integration

import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.domain.QuoteStatus
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.AddQuoteLineItemRequest
import com.forgeflow.dto.CreatePricingRuleRequest
import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.CreateQuoteRequest
import com.forgeflow.dto.OrderResponse
import com.forgeflow.dto.ProductResponse
import com.forgeflow.dto.QuoteResponse
import com.forgeflow.dto.UpdateQuoteStatusRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Drives the whole CPQ flow (product -> pricing rule -> quote -> line item -> approve -> convert)
 * through the real REST API against a real Postgres container, verifying the AREA_BASED strategy's
 * price actually lands in the resulting Order — the same happy path this project was manually
 * curl-tested against during development, now automated.
 */
class QuoteToOrderFlowIntegrationTest : AbstractIntegrationTest() {

	@Test
	fun `converting an approved quote creates a priced order`() {
		val tenant = registerTenant("flow")
		val auth = "Bearer ${tenant.token}"

		val productJson = mockMvc.perform(
			post("/api/v1/products")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreateProductRequest(
							sku = "PANEL-FLOW",
							name = "Steel Panel",
							description = null,
							baseUnitPrice = BigDecimal("20.00"),
							unitOfMeasure = UnitOfMeasure.SQUARE_METER,
						),
					),
				),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val product = objectMapper.readValue(productJson, ProductResponse::class.java)

		mockMvc.perform(
			post("/api/v1/products/${product.id}/pricing-rules")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreatePricingRuleRequest(PricingStrategyType.AREA_BASED, mapOf("multiplier" to 1.0), 0),
					),
				),
		).andExpect(status().isCreated)

		val quoteJson = mockMvc.perform(
			post("/api/v1/quotes")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(CreateQuoteRequest("Contoso Mfg", "buyer@contoso.test"))),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val quote = objectMapper.readValue(quoteJson, QuoteResponse::class.java)

		mockMvc.perform(
			post("/api/v1/quotes/${quote.id}/line-items")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						AddQuoteLineItemRequest(product.id, BigDecimal("1"), BigDecimal("2.0"), BigDecimal("1.5")),
					),
				),
		).andExpect(status().isOk)

		mockMvc.perform(
			put("/api/v1/quotes/${quote.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdateQuoteStatusRequest(QuoteStatus.APPROVED))),
		).andExpect(status().isOk)

		mockMvc.perform(
			put("/api/v1/quotes/${quote.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdateQuoteStatusRequest(QuoteStatus.CONVERTED_TO_ORDER))),
		).andExpect(status().isOk)

		val ordersJson = mockMvc.perform(get("/api/v1/orders").header("Authorization", auth))
			.andExpect(status().isOk)
			.andReturn().response.contentAsString
		val orders = objectMapper.readValue(ordersJson, Array<OrderResponse>::class.java)

		assertEquals(1, orders.size)
		assertEquals(quote.id, orders[0].quoteId)
		// 20.00 * 2.0 * 1.5 * 1.0 = 60.00
		assertEquals(BigDecimal("60.0000"), orders[0].totalAmount)
	}
}
