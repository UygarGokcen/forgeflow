package com.forgeflow.controller

import com.forgeflow.dto.CreateMaterialRequest
import com.forgeflow.dto.MaterialResponse
import com.forgeflow.dto.UpdateMaterialRequest
import com.forgeflow.service.MaterialService
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
@RequestMapping("/api/v1/materials")
class MaterialController(
	private val materialService: MaterialService,
) {

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	fun create(@Valid @RequestBody request: CreateMaterialRequest): ResponseEntity<MaterialResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(materialService.create(request))

	@GetMapping
	fun list(): ResponseEntity<List<MaterialResponse>> = ResponseEntity.ok(materialService.list())

	@GetMapping("/low-stock")
	fun listLowStock(): ResponseEntity<List<MaterialResponse>> = ResponseEntity.ok(materialService.listLowStock())

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<MaterialResponse> = ResponseEntity.ok(materialService.get(id))

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateMaterialRequest,
	): ResponseEntity<MaterialResponse> = ResponseEntity.ok(materialService.update(id, request))

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
		materialService.delete(id)
		return ResponseEntity.noContent().build()
	}
}
