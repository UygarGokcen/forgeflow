package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Material
import com.forgeflow.domain.ProductMaterial
import com.forgeflow.dto.AddProductMaterialRequest
import com.forgeflow.dto.ProductMaterialResponse
import com.forgeflow.exception.DuplicateRecipeEntryException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.ProductMaterialRepository
import com.forgeflow.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Manages which materials a product is made from, and how much of each it uses. */
@Service
class ProductRecipeService(
	private val productMaterialRepository: ProductMaterialRepository,
	private val productRepository: ProductRepository,
	private val materialRepository: MaterialRepository,
) {

	@Transactional
	fun add(productId: UUID, request: AddProductMaterialRequest): ProductMaterialResponse {
		val tenantId = TenantContext.getCurrentTenant()
		ensureProductExists(tenantId, productId)

		val material = materialRepository.findByTenantIdAndId(tenantId, request.materialId)
			?: throw ResourceNotFoundException("Material ${request.materialId} not found")

		if (productMaterialRepository.existsByTenantIdAndProductIdAndMaterialId(tenantId, productId, material.id!!)) {
			throw DuplicateRecipeEntryException(material.id!!)
		}

		val entry = productMaterialRepository.save(
			ProductMaterial(
				tenantId = tenantId,
				productId = productId,
				materialId = material.id!!,
				quantityPerUnit = request.quantityPerUnit,
			),
		)
		return entry.toResponse(material)
	}

	@Transactional(readOnly = true)
	fun list(productId: UUID): List<ProductMaterialResponse> {
		val tenantId = TenantContext.getCurrentTenant()
		ensureProductExists(tenantId, productId)

		val entries = productMaterialRepository.findAllByTenantIdAndProductId(tenantId, productId)
		if (entries.isEmpty()) return emptyList()

		val materialsById = materialRepository
			.findAllByTenantIdAndIdIn(tenantId, entries.map { it.materialId })
			.associateBy { it.id!! }

		return entries.mapNotNull { entry -> materialsById[entry.materialId]?.let { entry.toResponse(it) } }
	}

	@Transactional
	fun remove(productId: UUID, entryId: UUID) {
		val tenantId = TenantContext.getCurrentTenant()
		val entry = productMaterialRepository.findByTenantIdAndProductIdAndId(tenantId, productId, entryId)
			?: throw ResourceNotFoundException("Recipe entry $entryId not found for product $productId")
		productMaterialRepository.delete(entry)
	}

	private fun ensureProductExists(tenantId: UUID, productId: UUID) {
		if (productRepository.findByTenantIdAndId(tenantId, productId) == null) {
			throw ResourceNotFoundException("Product $productId not found")
		}
	}

	private fun ProductMaterial.toResponse(material: Material) = ProductMaterialResponse(
		id = id!!,
		productId = productId,
		materialId = materialId,
		materialSku = material.sku,
		materialName = material.name,
		materialUnitOfMeasure = material.unitOfMeasure,
		quantityPerUnit = quantityPerUnit,
	)
}
