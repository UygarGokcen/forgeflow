package com.forgeflow.controller

import com.forgeflow.dto.CreatePricingRuleRequest
import com.forgeflow.dto.PricingRuleResponse
import com.forgeflow.service.PricingRuleService
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
@RequestMapping("/api/v1/products/{productId}/pricing-rules")
class PricingRuleController(
	private val pricingRuleService: PricingRuleService,
) {

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	fun create(
		@PathVariable productId: UUID,
		@Valid @RequestBody request: CreatePricingRuleRequest,
	): ResponseEntity<PricingRuleResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(pricingRuleService.create(productId, request))

	@GetMapping
	fun list(@PathVariable productId: UUID): ResponseEntity<List<PricingRuleResponse>> =
		ResponseEntity.ok(pricingRuleService.list(productId))

	@DeleteMapping("/{ruleId}")
	@PreAuthorize("hasRole('ADMIN')")
	fun delete(@PathVariable productId: UUID, @PathVariable ruleId: UUID): ResponseEntity<Void> {
		pricingRuleService.delete(productId, ruleId)
		return ResponseEntity.noContent().build()
	}
}
