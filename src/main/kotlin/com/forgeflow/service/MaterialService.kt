package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Material
import com.forgeflow.domain.StockMovement
import com.forgeflow.domain.StockMovementReason
import com.forgeflow.dto.AdjustStockRequest
import com.forgeflow.dto.CreateMaterialRequest
import com.forgeflow.dto.MaterialResponse
import com.forgeflow.dto.StockMovementResponse
import com.forgeflow.dto.UpdateMaterialRequest
import com.forgeflow.exception.DuplicateMaterialSkuException
import com.forgeflow.exception.InvalidStockAdjustmentException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.StockMovementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class MaterialService(
	private val materialRepository: MaterialRepository,
	private val stockMovementRepository: StockMovementRepository,
) {

	@Transactional
	fun create(request: CreateMaterialRequest): MaterialResponse {
		val tenantId = TenantContext.getCurrentTenant()
		if (materialRepository.existsByTenantIdAndSku(tenantId, request.sku)) {
			throw DuplicateMaterialSkuException(request.sku)
		}

		val material = materialRepository.save(
			Material(
				tenantId = tenantId,
				sku = request.sku,
				name = request.name,
				unitOfMeasure = request.unitOfMeasure,
				stockQuantity = request.stockQuantity,
				reorderLevel = request.reorderLevel,
			),
		)

		if (request.stockQuantity > BigDecimal.ZERO) {
			stockMovementRepository.save(
				StockMovement(
					tenantId = tenantId,
					materialId = material.id!!,
					quantityDelta = request.stockQuantity,
					balanceAfter = material.stockQuantity,
					reason = StockMovementReason.INITIAL_STOCK,
				),
			)
		}

		return material.toResponse()
	}

	@Transactional(readOnly = true)
	fun list(): List<MaterialResponse> =
		materialRepository.findAllByTenantId(TenantContext.getCurrentTenant()).map { it.toResponse() }

	@Transactional(readOnly = true)
	fun get(id: UUID): MaterialResponse = findOwned(id).toResponse()

	/** Materials at or below their reorder level, so someone can order more in time. */
	@Transactional(readOnly = true)
	fun listLowStock(): List<MaterialResponse> =
		materialRepository.findLowStock(TenantContext.getCurrentTenant()).map { it.toResponse() }

	@Transactional
	fun update(id: UUID, request: UpdateMaterialRequest): MaterialResponse {
		val material = findOwned(id)
		material.name = request.name
		material.reorderLevel = request.reorderLevel
		// Flush here so the response carries the new updatedAt. The auditing listener only sets
		// it during flush, so without this we would return the value from before the update.
		return materialRepository.saveAndFlush(material).toResponse()
	}

	/**
	 * The only way stock quantity ever changes by hand — every call writes a
	 * [StockMovementReason.MANUAL_ADJUSTMENT] row, so there is always a ledger entry for why the
	 * number changed. [request]'s `quantityDelta` can be positive (a delivery, a recount finding
	 * more) or negative (damage, a recount finding less); it is never applied directly to
	 * `stockQuantity` without going through this ledger.
	 */
	@Transactional
	fun adjustStock(id: UUID, request: AdjustStockRequest): MaterialResponse {
		val material = findOwned(id)
		val resulting = material.stockQuantity.add(request.quantityDelta)
		if (resulting < BigDecimal.ZERO) {
			throw InvalidStockAdjustmentException(
				"Adjusting ${material.sku} by ${request.quantityDelta} would leave $resulting in stock",
			)
		}

		material.stockQuantity = resulting
		val saved = materialRepository.saveAndFlush(material)

		stockMovementRepository.save(
			StockMovement(
				tenantId = material.tenantId,
				materialId = material.id!!,
				quantityDelta = request.quantityDelta,
				balanceAfter = resulting,
				reason = StockMovementReason.MANUAL_ADJUSTMENT,
				note = request.note,
			),
		)

		return saved.toResponse()
	}

	@Transactional(readOnly = true)
	fun listMovements(id: UUID): List<StockMovementResponse> {
		val tenantId = TenantContext.getCurrentTenant()
		findOwned(id)
		return stockMovementRepository.findAllByTenantIdAndMaterialIdOrderByCreatedAtDesc(tenantId, id)
			.map { it.toResponse() }
	}

	@Transactional
	fun delete(id: UUID) {
		materialRepository.delete(findOwned(id))
	}

	private fun findOwned(id: UUID): Material =
		materialRepository.findByTenantIdAndId(TenantContext.getCurrentTenant(), id)
			?: throw ResourceNotFoundException("Material $id not found")

	private fun Material.toResponse() = MaterialResponse(
		id = id!!,
		sku = sku,
		name = name,
		unitOfMeasure = unitOfMeasure,
		stockQuantity = stockQuantity,
		reorderLevel = reorderLevel,
		belowReorderLevel = stockQuantity <= reorderLevel,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)

	private fun StockMovement.toResponse() = StockMovementResponse(
		id = id!!,
		materialId = materialId,
		quantityDelta = quantityDelta,
		balanceAfter = balanceAfter,
		reason = reason,
		referenceId = referenceId,
		note = note,
		createdAt = createdAt,
	)
}
