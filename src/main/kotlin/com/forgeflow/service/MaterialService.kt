package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Material
import com.forgeflow.dto.CreateMaterialRequest
import com.forgeflow.dto.MaterialResponse
import com.forgeflow.dto.UpdateMaterialRequest
import com.forgeflow.exception.DuplicateMaterialSkuException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.MaterialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MaterialService(
	private val materialRepository: MaterialRepository,
) {

	@Transactional
	fun create(request: CreateMaterialRequest): MaterialResponse {
		val tenantId = TenantContext.getCurrentTenant()
		if (materialRepository.existsByTenantIdAndSku(tenantId, request.sku)) {
			throw DuplicateMaterialSkuException(request.sku)
		}

		return materialRepository.save(
			Material(
				tenantId = tenantId,
				sku = request.sku,
				name = request.name,
				unitOfMeasure = request.unitOfMeasure,
				stockQuantity = request.stockQuantity,
				reorderLevel = request.reorderLevel,
			),
		).toResponse()
	}

	@Transactional(readOnly = true)
	fun list(): List<MaterialResponse> =
		materialRepository.findAllByTenantId(TenantContext.getCurrentTenant()).map { it.toResponse() }

	@Transactional(readOnly = true)
	fun get(id: UUID): MaterialResponse = findOwned(id).toResponse()

	/** Materials at or below their reorder level — what a purchasing officer actually needs to see. */
	@Transactional(readOnly = true)
	fun listLowStock(): List<MaterialResponse> =
		materialRepository.findLowStock(TenantContext.getCurrentTenant()).map { it.toResponse() }

	@Transactional
	fun update(id: UUID, request: UpdateMaterialRequest): MaterialResponse {
		val material = findOwned(id)
		material.name = request.name
		material.stockQuantity = request.stockQuantity
		material.reorderLevel = request.reorderLevel
		// Flush so @LastModifiedDate (set by the auditing listener at flush time) is reflected in
		// the response instead of the stale in-memory value from before this update.
		return materialRepository.saveAndFlush(material).toResponse()
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
}
