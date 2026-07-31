package com.forgeflow.controller

import com.forgeflow.dto.CreatePurchaseOrderRequest
import com.forgeflow.dto.PurchaseOrderResponse
import com.forgeflow.dto.UpdatePurchaseOrderStatusRequest
import com.forgeflow.service.PurchaseOrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/purchase-orders")
class PurchaseOrderController(
	private val purchaseOrderService: PurchaseOrderService,
) {

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	fun create(@Valid @RequestBody request: CreatePurchaseOrderRequest): ResponseEntity<PurchaseOrderResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.create(request))

	@GetMapping
	fun list(): ResponseEntity<List<PurchaseOrderResponse>> = ResponseEntity.ok(purchaseOrderService.list())

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<PurchaseOrderResponse> =
		ResponseEntity.ok(purchaseOrderService.get(id))

	@PutMapping("/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdatePurchaseOrderStatusRequest,
	): ResponseEntity<PurchaseOrderResponse> = ResponseEntity.ok(purchaseOrderService.updateStatus(id, request.status))
}
