package com.forgeflow.controller

import com.forgeflow.dto.OrderResponse
import com.forgeflow.dto.UpdateOrderStatusRequest
import com.forgeflow.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
	private val orderService: OrderService,
) {

	@GetMapping
	fun list(): ResponseEntity<List<OrderResponse>> = ResponseEntity.ok(orderService.list())

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<OrderResponse> = ResponseEntity.ok(orderService.get(id))

	@PutMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateOrderStatusRequest,
	): ResponseEntity<OrderResponse> = ResponseEntity.ok(orderService.updateStatus(id, request.status))
}
