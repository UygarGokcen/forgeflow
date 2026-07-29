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

class DuplicateMaterialSkuException(sku: String) :
	ApiException(HttpStatus.CONFLICT, "A material with SKU '$sku' already exists")

class DuplicateRecipeEntryException(materialId: java.util.UUID) :
	ApiException(HttpStatus.CONFLICT, "Material $materialId is already part of this product's recipe")

/**
 * Thrown when converting a quote would use more material than there is in stock. It follows the
 * same idea as the other lifecycle checks: an empty quote can't be approved, and a quote the shop
 * can't actually build doesn't become an order.
 */
class InsufficientStockException(shortfalls: List<String>) :
	ApiException(
		HttpStatus.CONFLICT,
		"Insufficient material stock to convert this quote: ${shortfalls.joinToString("; ")}",
	)
