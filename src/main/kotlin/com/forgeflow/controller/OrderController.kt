package com.forgeflow.controller

import com.forgeflow.dto.OrderResponse
import com.forgeflow.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
}
