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

class QuoteNotEditableException(quoteId: java.util.UUID, status: String) :
	ApiException(HttpStatus.CONFLICT, "Quote $quoteId is $status and can no longer be edited")

class InvalidQuoteStatusTransitionException(from: String, to: String) :
	ApiException(HttpStatus.CONFLICT, "Cannot transition a quote from $from to $to")

class EmptyQuoteException(quoteId: java.util.UUID) :
	ApiException(HttpStatus.CONFLICT, "Quote $quoteId has no line items and cannot be approved")
