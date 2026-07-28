package com.forgeflow.controller

import com.forgeflow.dto.CreateProductRequest
import com.forgeflow.dto.ProductResponse
import com.forgeflow.dto.UpdateProductRequest
import com.forgeflow.service.ProductService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
	private val productService: ProductService,
) {

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	fun create(@Valid @RequestBody request: CreateProductRequest): ResponseEntity<ProductResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request))

	@GetMapping
	fun list(): ResponseEntity<List<ProductResponse>> = ResponseEntity.ok(productService.list())

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<ProductResponse> = ResponseEntity.ok(productService.get(id))

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateProductRequest): ResponseEntity<ProductResponse> =
		ResponseEntity.ok(productService.update(id, request))

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
		productService.delete(id)
		return ResponseEntity.noContent().build()
	}
}
