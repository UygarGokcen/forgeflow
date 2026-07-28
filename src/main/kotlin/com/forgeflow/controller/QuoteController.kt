package com.forgeflow.controller

import com.forgeflow.dto.AddQuoteLineItemRequest
import com.forgeflow.dto.CreateQuoteRequest
import com.forgeflow.dto.QuoteResponse
import com.forgeflow.dto.UpdateQuoteStatusRequest
import com.forgeflow.service.QuoteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
@RequestMapping("/api/v1/quotes")
class QuoteController(
	private val quoteService: QuoteService,
) {

	@PostMapping
	fun create(@Valid @RequestBody request: CreateQuoteRequest): ResponseEntity<QuoteResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(quoteService.create(request))

	@GetMapping
	fun list(): ResponseEntity<List<QuoteResponse>> = ResponseEntity.ok(quoteService.list())

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<QuoteResponse> = ResponseEntity.ok(quoteService.get(id))

	@PostMapping("/{id}/line-items")
	fun addLineItem(
		@PathVariable id: UUID,
		@Valid @RequestBody request: AddQuoteLineItemRequest,
	): ResponseEntity<QuoteResponse> = ResponseEntity.ok(quoteService.addLineItem(id, request))

	@DeleteMapping("/{id}/line-items/{lineItemId}")
	fun removeLineItem(@PathVariable id: UUID, @PathVariable lineItemId: UUID): ResponseEntity<QuoteResponse> =
		ResponseEntity.ok(quoteService.removeLineItem(id, lineItemId))

	@PutMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateQuoteStatusRequest,
	): ResponseEntity<QuoteResponse> = ResponseEntity.ok(quoteService.updateStatus(id, request.status))
}
