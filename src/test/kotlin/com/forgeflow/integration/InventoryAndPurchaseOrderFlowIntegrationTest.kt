package com.forgeflow.integration

import com.forgeflow.domain.OrderStatus
import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.domain.PurchaseOrderStatus
import com.forgeflow.domain.QuoteStatus
import com.forgeflow.domain.StockMovementReason
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.AddProductMaterialRequest
import com.forgeflow.dto.AddQuoteLineItemRequest
import com.forgeflow.dto.CreateMaterialRequest
import com.forgeflow.dto.CreatePricingRuleRequest
import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.CreatePurchaseOrderLineItemRequest
import com.forgeflow.dto.CreatePurchaseOrderRequest
import com.forgeflow.dto.CreateQuoteRequest
import com.forgeflow.dto.MaterialResponse
import com.forgeflow.dto.OrderNotificationResponse
import com.forgeflow.dto.OrderResponse
import com.forgeflow.dto.ProductResponse
import com.forgeflow.dto.PurchaseOrderResponse
import com.forgeflow.dto.QuoteResponse
import com.forgeflow.dto.StockMovementResponse
import com.forgeflow.dto.UpdateOrderStatusRequest
import com.forgeflow.dto.UpdatePurchaseOrderStatusRequest
import com.forgeflow.dto.UpdateQuoteStatusRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Drives the parts of the flow [QuoteToOrderFlowIntegrationTest] doesn't touch: material
 * consumption on conversion, the Kafka-driven notification that follows it, the order's own
 * post-conversion lifecycle, and closing the loop with a purchase order — all against real
 * Postgres, Redis and Kafka containers, the same way the rest of this project verifies behaviour
 * mocks can't: RLS, DB constraints, and an actual message round-tripping through a broker.
 */
class InventoryAndPurchaseOrderFlowIntegrationTest : AbstractIntegrationTest() {

	@Test
	fun `converting a quote draws material, notifies over Kafka, and a purchase order restocks it`() {
		val tenant = registerTenant("inventory-flow")
		val auth = "Bearer ${tenant.token}"

		val materialJson = mockMvc.perform(
			post("/api/v1/materials")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreateMaterialRequest(
							sku = "SHEET-FLOW",
							name = "Steel sheet",
							unitOfMeasure = UnitOfMeasure.SQUARE_METER,
							stockQuantity = BigDecimal("100"),
							reorderLevel = BigDecimal("10"),
						),
					),
				),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val material = objectMapper.readValue(materialJson, MaterialResponse::class.java)

		val productJson = mockMvc.perform(
			post("/api/v1/products")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreateProductRequest(
							sku = "PANEL-FLOW-2",
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

		// One square meter of panel uses 1.1 m2 of sheet, a waste allowance.
		mockMvc.perform(
			post("/api/v1/products/${product.id}/materials")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(AddProductMaterialRequest(material.id, BigDecimal("1.1")))),
		).andExpect(status().isCreated)

		val quoteJson = mockMvc.perform(
			post("/api/v1/quotes")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(CreateQuoteRequest("Contoso Mfg", "buyer@contoso.test"))),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val quote = objectMapper.readValue(quoteJson, QuoteResponse::class.java)

		// 2.0m x 1.5m = 3 m2, one of them, so this draws 3 * 1.1 = 3.3 m2 of sheet.
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
				.content(
					objectMapper.writeValueAsString(
						UpdateQuoteStatusRequest(QuoteStatus.CONVERTED_TO_ORDER),
					),
				),
		).andExpect(status().isOk)

		val ordersJson = mockMvc.perform(get("/api/v1/orders").header("Authorization", auth))
			.andExpect(status().isOk).andReturn().response.contentAsString
		val order = objectMapper.readValue(ordersJson, Array<OrderResponse>::class.java).single()
		assertEquals(quote.id, order.quoteId)

		fun getMaterial(): MaterialResponse {
			val json = mockMvc.perform(get("/api/v1/materials/${material.id}").header("Authorization", auth))
				.andExpect(status().isOk).andReturn().response.contentAsString
			return objectMapper.readValue(json, MaterialResponse::class.java)
		}

		fun getMovements(): List<StockMovementResponse> {
			val json = mockMvc.perform(get("/api/v1/materials/${material.id}/movements").header("Authorization", auth))
				.andExpect(status().isOk).andReturn().response.contentAsString
			return objectMapper.readValue(json, Array<StockMovementResponse>::class.java).toList()
		}

		// 100 - 3.3 = 96.7, and the ledger shows exactly why.
		assertTrue(BigDecimal("96.7").compareTo(getMaterial().stockQuantity) == 0)
		val consumption = getMovements().single { it.reason == StockMovementReason.CONSUMPTION }
		assertEquals(quote.id, consumption.referenceId)
		assertTrue(BigDecimal("-3.3").compareTo(consumption.quantityDelta) == 0)

		// The Kafka consumer runs on its own thread, so this has to be polled for.
		val notifications = awaitUntil {
			val json = mockMvc.perform(get("/api/v1/orders/${order.id}/notifications").header("Authorization", auth))
				.andExpect(status().isOk).andReturn().response.contentAsString
			objectMapper.readValue(json, Array<OrderNotificationResponse>::class.java)
				.toList().ifEmpty { null }
		}
		assertEquals("buyer@contoso.test", notifications.single().recipient)

		mockMvc.perform(
			put("/api/v1/orders/${order.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdateOrderStatusRequest(OrderStatus.IN_PRODUCTION))),
		).andExpect(status().isOk)
		mockMvc.perform(
			put("/api/v1/orders/${order.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdateOrderStatusRequest(OrderStatus.SHIPPED))),
		).andExpect(status().isOk)
		// CONFIRMED is not reachable from SHIPPED.
		mockMvc.perform(
			put("/api/v1/orders/${order.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdateOrderStatusRequest(OrderStatus.CONFIRMED))),
		).andExpect(status().isConflict)

		val poJson = mockMvc.perform(
			post("/api/v1/purchase-orders")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreatePurchaseOrderRequest(
							supplierName = "Acme Steel",
							lineItems = listOf(CreatePurchaseOrderLineItemRequest(material.id, BigDecimal("50"))),
						),
					),
				),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val purchaseOrder = objectMapper.readValue(poJson, PurchaseOrderResponse::class.java)

		mockMvc.perform(
			put("/api/v1/purchase-orders/${purchaseOrder.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdatePurchaseOrderStatusRequest(PurchaseOrderStatus.SUBMITTED))),
		).andExpect(status().isOk)
		mockMvc.perform(
			put("/api/v1/purchase-orders/${purchaseOrder.id}/status")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(UpdatePurchaseOrderStatusRequest(PurchaseOrderStatus.RECEIVED))),
		).andExpect(status().isOk)

		// 96.7 + 50 = 146.7, and the ledger now shows both the draw and the restock.
		assertTrue(BigDecimal("146.7").compareTo(getMaterial().stockQuantity) == 0)
		val receipt = getMovements().single { it.reason == StockMovementReason.PURCHASE_RECEIPT }
		assertEquals(purchaseOrder.id, receipt.referenceId)
		assertTrue(BigDecimal("50").compareTo(receipt.quantityDelta) == 0)
	}
}
