package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Product
import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.ProductResponse
import com.forgeflow.dto.UpdateProductRequest
import com.forgeflow.exception.DuplicateSkuException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProductService(
	private val productRepository: ProductRepository,
	private val pricingLookupCache: PricingLookupCache,
) {

	@Transactional
	fun create(request: CreateProductRequest): ProductResponse {
		val tenantId = TenantContext.getCurrentTenant()
		if (productRepository.existsByTenantIdAndSku(tenantId, request.sku)) {
			throw DuplicateSkuException(request.sku)
		}

		val product = productRepository.save(
			Product(
				tenantId = tenantId,
				sku = request.sku,
				name = request.name,
				description = request.description,
				baseUnitPrice = request.baseUnitPrice,
				unitOfMeasure = request.unitOfMeasure,
			),
		)
		return product.toResponse()
	}

	@Transactional(readOnly = true)
	fun list(): List<ProductResponse> =
		productRepository.findAllByTenantId(TenantContext.getCurrentTenant()).map { it.toResponse() }

	@Transactional(readOnly = true)
	fun get(id: UUID): ProductResponse = findOwned(id).toResponse()

	@Transactional
	fun update(id: UUID, request: UpdateProductRequest): ProductResponse {
		val product = findOwned(id)
		product.name = request.name
		product.description = request.description
		product.baseUnitPrice = request.baseUnitPrice
		product.unitOfMeasure = request.unitOfMeasure
		product.isActive = request.isActive
		// Flush here so the response carries the new updatedAt. The auditing listener only sets
		// it during flush, so without this we would return the value from before the update.
		val response = productRepository.saveAndFlush(product).toResponse()
		pricingLookupCache.evictProduct(product.tenantId, id)
		return response
	}

	@Transactional
	fun delete(id: UUID) {
		val product = findOwned(id)
		val tenantId = product.tenantId
		productRepository.delete(product)
		pricingLookupCache.evictProduct(tenantId, id)
	}

	private fun findOwned(id: UUID): Product =
		productRepository.findByTenantIdAndId(TenantContext.getCurrentTenant(), id)
			?: throw ResourceNotFoundException("Product $id not found")

	private fun Product.toResponse() = ProductResponse(
		id = id!!,
		sku = sku,
		name = name,
		description = description,
		baseUnitPrice = baseUnitPrice,
		unitOfMeasure = unitOfMeasure,
		isActive = isActive,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}
