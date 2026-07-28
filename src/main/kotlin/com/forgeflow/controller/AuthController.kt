package com.forgeflow.controller

import com.forgeflow.dto.AuthResponse
import com.forgeflow.dto.LoginRequest
import com.forgeflow.dto.RegisterTenantRequest
import com.forgeflow.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
	private val authService: AuthService,
) {

	@PostMapping("/register-tenant")
	fun registerTenant(@Valid @RequestBody request: RegisterTenantRequest): ResponseEntity<AuthResponse> =
		ResponseEntity.status(HttpStatus.CREATED).body(authService.registerTenant(request))

	@PostMapping("/login")
	fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
		ResponseEntity.ok(authService.login(request))
}
