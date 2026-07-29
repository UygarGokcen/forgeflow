package com.forgeflow.controller

import com.forgeflow.dto.AddProductMaterialRequest
import com.forgeflow.dto.ProductMaterialResponse
import com.forgeflow.service.ProductRecipeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products/{productId}/materials")
class ProductRecipeController(
	private val productRecipeService: ProductRecipeService,
) {

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	fun add(
		@PathVariable productId: UUID,
		@Valid @RequestBody request: AddProductMaterialRequest,
	): ResponseEntity<ProductMaterialResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(productRecipeService.add(productId, request))

	@GetMapping
	fun list(@PathVariable productId: UUID): ResponseEntity<List<ProductMaterialResponse>> =
		ResponseEntity.ok(productRecipeService.list(productId))

	@DeleteMapping("/{entryId}")
	@PreAuthorize("hasRole('ADMIN')")
	fun remove(@PathVariable productId: UUID, @PathVariable entryId: UUID): ResponseEntity<Void> {
		productRecipeService.remove(productId, entryId)
		return ResponseEntity.noContent().build()
	}
}
