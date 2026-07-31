package com.forgeflow.service

import com.forgeflow.domain.Material
import com.forgeflow.domain.PurchaseOrder
import com.forgeflow.domain.PurchaseOrderLineItem
import com.forgeflow.domain.PurchaseOrderStatus
import com.forgeflow.domain.StockMovement
import com.forgeflow.domain.StockMovementReason
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.CreatePurchaseOrderLineItemRequest
import com.forgeflow.dto.CreatePurchaseOrderRequest
import com.forgeflow.exception.EmptyPurchaseOrderException
import com.forgeflow.exception.InvalidPurchaseOrderStatusTransitionException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.PurchaseOrderLineItemRepository
import com.forgeflow.repository.PurchaseOrderRepository
import com.forgeflow.repository.StockMovementRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class PurchaseOrderServiceTest {

	private val purchaseOrderRepository: PurchaseOrderRepository = mock()
	private val purchaseOrderLineItemRepository: PurchaseOrderLineItemRepository = mock()
	private val materialRepository: MaterialRepository = mock()
	private val stockMovementRepository: StockMovementRepository = mock()

	private val purchaseOrderService = PurchaseOrderService(
		purchaseOrderRepository,
		purchaseOrderLineItemRepository,
		materialRepository,
		stockMovementRepository,
	)

	private val tenantId: UUID = UUID.randomUUID()

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	private fun material(stock: BigDecimal) = Material(
		tenantId = tenantId,
		sku = "STEEL-01",
		name = "Steel sheet",
		unitOfMeasure = UnitOfMeasure.KILOGRAM,
		stockQuantity = stock,
		id = UUID.randomUUID(),
	)

	@Test
	fun `create rejects a purchase order with no line items`() {
		assertThrows(EmptyPurchaseOrderException::class.java) {
			purchaseOrderService.create(CreatePurchaseOrderRequest(supplierName = "Acme Steel", lineItems = emptyList()))
		}
	}

	@Test
	fun `create rejects a line item for a material that doesn't exist for this tenant`() {
		val materialId = UUID.randomUUID()
		whenever(materialRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(emptyList())

		assertThrows(ResourceNotFoundException::class.java) {
			purchaseOrderService.create(
				CreatePurchaseOrderRequest(
					supplierName = "Acme Steel",
					lineItems = listOf(CreatePurchaseOrderLineItemRequest(materialId, BigDecimal("10"))),
				),
			)
		}
	}

	@Test
	fun `create combines two line items for the same material into one`() {
		val material = material(stock = BigDecimal("50"))
		whenever(materialRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(listOf(material))
		doAnswer {
			(it.arguments[0] as PurchaseOrder).also { po -> po.id = UUID.randomUUID() }
		}.whenever(purchaseOrderRepository).save(any())
		doAnswer {
			(it.arguments[0] as PurchaseOrderLineItem).also { line -> line.id = UUID.randomUUID() }
		}.whenever(purchaseOrderLineItemRepository).save(any())

		val response = purchaseOrderService.create(
			CreatePurchaseOrderRequest(
				supplierName = "Acme Steel",
				lineItems = listOf(
					CreatePurchaseOrderLineItemRequest(material.id!!, BigDecimal("10")),
					CreatePurchaseOrderLineItemRequest(material.id!!, BigDecimal("5")),
				),
			),
		)

		assertEquals(1, response.lineItems.size)
		assertTrue(BigDecimal("15").compareTo(response.lineItems.single().quantityOrdered) == 0)
	}

	private fun purchaseOrder(status: PurchaseOrderStatus) = PurchaseOrder(
		tenantId = tenantId,
		poNumber = "PO-20260101-0001",
		supplierName = "Acme Steel",
		status = status,
		createdBy = UUID.randomUUID(),
		id = UUID.randomUUID(),
	)

	@Test
	fun `a draft purchase order can be submitted`() {
		val po = purchaseOrder(PurchaseOrderStatus.DRAFT)
		whenever(purchaseOrderRepository.findByTenantIdAndId(tenantId, po.id!!)).thenReturn(po)
		whenever(purchaseOrderLineItemRepository.findAllByTenantIdAndPurchaseOrderId(tenantId, po.id!!))
			.thenReturn(emptyList())
		doAnswer { it.arguments[0] as PurchaseOrder }.whenever(purchaseOrderRepository).saveAndFlush(any())
		whenever(materialRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(emptyList())

		val response = purchaseOrderService.updateStatus(po.id!!, PurchaseOrderStatus.SUBMITTED)

		assertEquals(PurchaseOrderStatus.SUBMITTED, response.status)
	}

	@Test
	fun `a received purchase order cannot transition anywhere`() {
		val po = purchaseOrder(PurchaseOrderStatus.RECEIVED)
		whenever(purchaseOrderRepository.findByTenantIdAndId(tenantId, po.id!!)).thenReturn(po)

		assertThrows(InvalidPurchaseOrderStatusTransitionException::class.java) {
			purchaseOrderService.updateStatus(po.id!!, PurchaseOrderStatus.CANCELLED)
		}
	}

	@Test
	fun `marking a submitted purchase order received adds stock and records a PURCHASE_RECEIPT movement`() {
		val material = material(stock = BigDecimal("50"))
		val po = purchaseOrder(PurchaseOrderStatus.SUBMITTED)
		val lineItem = PurchaseOrderLineItem(
			tenantId = tenantId,
			purchaseOrderId = po.id!!,
			materialId = material.id!!,
			quantityOrdered = BigDecimal("20"),
			id = UUID.randomUUID(),
		)
		whenever(purchaseOrderRepository.findByTenantIdAndId(tenantId, po.id!!)).thenReturn(po)
		whenever(purchaseOrderLineItemRepository.findAllByTenantIdAndPurchaseOrderId(tenantId, po.id!!))
			.thenReturn(listOf(lineItem))
		whenever(materialRepository.lockAllByTenantIdAndIdIn(tenantId, listOf(material.id!!)))
			.thenReturn(listOf(material))
		whenever(materialRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(listOf(material))
		doAnswer { it.arguments[0] as PurchaseOrder }.whenever(purchaseOrderRepository).saveAndFlush(any())

		val response = purchaseOrderService.updateStatus(po.id!!, PurchaseOrderStatus.RECEIVED)

		assertEquals(PurchaseOrderStatus.RECEIVED, response.status)
		assertTrue(BigDecimal("70").compareTo(material.stockQuantity) == 0)

		val captor = argumentCaptor<List<StockMovement>>()
		verify(stockMovementRepository).saveAll(captor.capture())
		val movement = captor.firstValue.single()
		assertEquals(StockMovementReason.PURCHASE_RECEIPT, movement.reason)
		assertEquals(po.id, movement.referenceId)
		assertTrue(BigDecimal("20").compareTo(movement.quantityDelta) == 0)
	}
}
