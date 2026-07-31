package com.forgeflow.service

import com.forgeflow.context.CurrentUser
import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Material
import com.forgeflow.domain.PurchaseOrder
import com.forgeflow.domain.PurchaseOrderLineItem
import com.forgeflow.domain.PurchaseOrderStatus
import com.forgeflow.domain.StockMovement
import com.forgeflow.domain.StockMovementReason
import com.forgeflow.dto.CreatePurchaseOrderRequest
import com.forgeflow.dto.PurchaseOrderLineItemResponse
import com.forgeflow.dto.PurchaseOrderResponse
import com.forgeflow.exception.EmptyPurchaseOrderException
import com.forgeflow.exception.InvalidPurchaseOrderStatusTransitionException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.PurchaseOrderLineItemRepository
import com.forgeflow.repository.PurchaseOrderRepository
import com.forgeflow.repository.StockMovementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PurchaseOrderService(
	private val purchaseOrderRepository: PurchaseOrderRepository,
	private val purchaseOrderLineItemRepository: PurchaseOrderLineItemRepository,
	private val materialRepository: MaterialRepository,
	private val stockMovementRepository: StockMovementRepository,
) {

	@Transactional
	fun create(request: CreatePurchaseOrderRequest): PurchaseOrderResponse {
		val tenantId = TenantContext.getCurrentTenant()
		if (request.lineItems.isEmpty()) throw EmptyPurchaseOrderException()

		// Ordering the same material twice on one PO is a mistake worth just fixing rather than
		// rejecting outright, so quantities for the same material are combined instead.
		val quantitiesByMaterial = request.lineItems
			.groupingBy { it.materialId }
			.fold(BigDecimal.ZERO) { total, line -> total.add(line.quantity) }

		val materials = materialRepository.findAllByTenantIdAndIdIn(tenantId, quantitiesByMaterial.keys)
			.associateBy { it.id!! }
		val missing = quantitiesByMaterial.keys - materials.keys
		if (missing.isNotEmpty()) throw ResourceNotFoundException("Material(s) not found: ${missing.joinToString()}")

		val purchaseOrder = purchaseOrderRepository.save(
			PurchaseOrder(
				tenantId = tenantId,
				poNumber = generatePoNumber(tenantId),
				supplierName = request.supplierName,
				createdBy = CurrentUser.getId(),
			),
		)

		val lineItems = quantitiesByMaterial.map { (materialId, quantity) ->
			purchaseOrderLineItemRepository.save(
				PurchaseOrderLineItem(
					tenantId = tenantId,
					purchaseOrderId = purchaseOrder.id!!,
					materialId = materialId,
					quantityOrdered = quantity,
				),
			)
		}

		return purchaseOrder.toResponse(lineItems, materials)
	}

	@Transactional(readOnly = true)
	fun list(): List<PurchaseOrderResponse> {
		val tenantId = TenantContext.getCurrentTenant()
		return purchaseOrderRepository.findAllByTenantId(tenantId).map { it.withLineItems(tenantId) }
	}

	@Transactional(readOnly = true)
	fun get(id: UUID): PurchaseOrderResponse {
		val tenantId = TenantContext.getCurrentTenant()
		return findOwned(tenantId, id).withLineItems(tenantId)
	}

	@Transactional
	fun updateStatus(id: UUID, newStatus: PurchaseOrderStatus): PurchaseOrderResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val purchaseOrder = findOwned(tenantId, id)

		val allowedNextStatuses = ALLOWED_TRANSITIONS.getValue(purchaseOrder.status)
		if (newStatus !in allowedNextStatuses) {
			throw InvalidPurchaseOrderStatusTransitionException(purchaseOrder.status.name, newStatus.name)
		}

		val lineItems = purchaseOrderLineItemRepository.findAllByTenantIdAndPurchaseOrderId(tenantId, id)

		if (newStatus == PurchaseOrderStatus.RECEIVED) {
			receiveIntoStock(tenantId, id, lineItems)
		}

		purchaseOrder.status = newStatus
		// Flush here so the response carries the new updatedAt. The auditing listener only sets
		// it during flush, so without this we would return the value from before the update.
		val saved = purchaseOrderRepository.saveAndFlush(purchaseOrder)

		val materials = materialRepository.findAllByTenantIdAndIdIn(tenantId, lineItems.map { it.materialId })
			.associateBy { it.id!! }
		return saved.toResponse(lineItems, materials)
	}

	/**
	 * Adds every line item's quantity back to its material's stock, in the same transaction as the
	 * status change — so a purchase order can't end up `RECEIVED` without the stock actually being
	 * there, the same guarantee [InventoryService.consumeForConversion] gives on the way out.
	 *
	 * Locks the material rows first, same reasoning and same `order by` as
	 * [MaterialRepository.lockAllByTenantIdAndIdIn]: a receipt landing at the same moment as a
	 * quote conversion drawing from the same material shouldn't be able to race it.
	 */
	private fun receiveIntoStock(tenantId: UUID, purchaseOrderId: UUID, lineItems: List<PurchaseOrderLineItem>) {
		if (lineItems.isEmpty()) return

		val materials = materialRepository
			.lockAllByTenantIdAndIdIn(tenantId, lineItems.map { it.materialId }.distinct().sorted())
			.associateBy { it.id!! }

		val movements = lineItems.map { line ->
			val material = materials.getValue(line.materialId)
			material.stockQuantity = material.stockQuantity.add(line.quantityOrdered)
			StockMovement(
				tenantId = tenantId,
				materialId = line.materialId,
				quantityDelta = line.quantityOrdered,
				balanceAfter = material.stockQuantity,
				reason = StockMovementReason.PURCHASE_RECEIPT,
				referenceId = purchaseOrderId,
			)
		}
		materialRepository.saveAll(materials.values)
		stockMovementRepository.saveAll(movements)
	}

	private fun findOwned(tenantId: UUID, id: UUID): PurchaseOrder =
		purchaseOrderRepository.findByTenantIdAndId(tenantId, id)
			?: throw ResourceNotFoundException("Purchase order $id not found")

	private fun PurchaseOrder.withLineItems(tenantId: UUID): PurchaseOrderResponse {
		val lineItems = purchaseOrderLineItemRepository.findAllByTenantIdAndPurchaseOrderId(tenantId, id!!)
		val materials = materialRepository.findAllByTenantIdAndIdIn(tenantId, lineItems.map { it.materialId })
			.associateBy { it.id!! }
		return toResponse(lineItems, materials)
	}

	private fun PurchaseOrder.toResponse(
		lineItems: List<PurchaseOrderLineItem>,
		materials: Map<UUID, Material>,
	) = PurchaseOrderResponse(
		id = id!!,
		poNumber = poNumber,
		supplierName = supplierName,
		status = status,
		lineItems = lineItems.map { line ->
			PurchaseOrderLineItemResponse(
				id = line.id!!,
				materialId = line.materialId,
				materialSku = materials[line.materialId]?.sku ?: "unknown",
				quantityOrdered = line.quantityOrdered,
			)
		},
		createdAt = createdAt,
		updatedAt = updatedAt,
	)

	private fun generatePoNumber(tenantId: UUID): String {
		val datePrefix = LocalDate.now().format(PO_NUMBER_DATE_FORMAT)
		repeat(10) {
			val candidate = "PO-$datePrefix-${(1000..9999).random()}"
			if (!purchaseOrderRepository.existsByTenantIdAndPoNumber(tenantId, candidate)) return candidate
		}
		error("Failed to generate a unique purchase order number after 10 attempts")
	}

	companion object {
		private val PO_NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

		/** Same shape as the quote and order state machines: an explicit map of what can follow
		 *  what, instead of a free-text column. */
		private val ALLOWED_TRANSITIONS: Map<PurchaseOrderStatus, Set<PurchaseOrderStatus>> = mapOf(
			PurchaseOrderStatus.DRAFT to setOf(PurchaseOrderStatus.SUBMITTED, PurchaseOrderStatus.CANCELLED),
			PurchaseOrderStatus.SUBMITTED to setOf(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CANCELLED),
			PurchaseOrderStatus.RECEIVED to emptySet(),
			PurchaseOrderStatus.CANCELLED to emptySet(),
		)
	}
}
