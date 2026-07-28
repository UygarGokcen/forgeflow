package com.forgeflow.exception

import org.springframework.http.HttpStatus

sealed class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

class TenantAlreadyExistsException(slug: String) :
	ApiException(HttpStatus.CONFLICT, "A tenant with slug '$slug' already exists")

class UserAlreadyExistsException(email: String) :
	ApiException(HttpStatus.CONFLICT, "A user with email '$email' already exists for this tenant")

class InvalidCredentialsException :
	ApiException(HttpStatus.UNAUTHORIZED, "Invalid tenant, email or password")

class ResourceNotFoundException(message: String) :
	ApiException(HttpStatus.NOT_FOUND, message)

class DuplicateSkuException(sku: String) :
	ApiException(HttpStatus.CONFLICT, "A product with SKU '$sku' already exists")

class InvalidPricingConfigException(message: String) :
	ApiException(HttpStatus.BAD_REQUEST, message)
