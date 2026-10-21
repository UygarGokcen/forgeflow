package com.forgeflow.exception

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.persistence.OptimisticLockException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mapping.PropertyReferenceException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

data class ApiError(
	val timestamp: Instant,
	val status: Int,
	val error: String,
	val message: String,
	val path: String,
	val fieldErrors: Map<String, String>? = null,
)

@RestControllerAdvice
class GlobalExceptionHandler {

	private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

	@ExceptionHandler(ApiException::class)
	fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ApiError> =
		respond(ex.status, ex.message ?: ex.status.reasonPhrase, request)

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
		val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid value") }
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
			ApiError(
				timestamp = Instant.now(),
				status = HttpStatus.BAD_REQUEST.value(),
				error = HttpStatus.BAD_REQUEST.reasonPhrase,
				message = "Validation failed",
				path = request.requestURI,
				fieldErrors = fieldErrors,
			),
		)
	}

	/**
	 * Two requests changed the same row at the same time and this one lost the race (see the
	 * `@Version` columns added in V10). Nothing was saved, so retrying is safe.
	 */
	@ExceptionHandler(OptimisticLockingFailureException::class, OptimisticLockException::class)
	fun handleConcurrentUpdate(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ApiError> =
		respond(
			HttpStatus.CONFLICT,
			"This record was changed by another request at the same time. Reload it and try again.",
			request,
		)

	/**
	 * `?sort=` names an entity property; an unknown one is a client mistake, not a server error.
	 * Spring Data sometimes wraps it in an InvalidDataAccessApiUsageException, so both are handled.
	 */
	@ExceptionHandler(PropertyReferenceException::class, InvalidDataAccessApiUsageException::class)
	fun handleBadSort(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ApiError> {
		val propertyError = ex as? PropertyReferenceException ?: ex.cause as? PropertyReferenceException
			?: return handleUnexpected(ex, request)
		return respond(HttpStatus.BAD_REQUEST, "Unknown sort property '${propertyError.propertyName}'", request)
	}

	/**
	 * `@PreAuthorize` throws from inside the controller call, so this advice sees it before Spring
	 * Security's own handling does. Without this it fell into the catch-all below as a 500.
	 */
	@ExceptionHandler(AccessDeniedException::class)
	fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ApiError> =
		respond(HttpStatus.FORBIDDEN, "You don't have permission to do this", request)

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
		clientError(ex)?.let { (status, message) -> return respond(status, message, request) }
		log.error("Unhandled exception while processing {} {}", request.method, request.requestURI, ex)
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request)
	}

	/**
	 * Client mistakes Spring MVC detects itself: an unknown URL, the wrong method or content type,
	 * a path variable that isn't a UUID, a body that isn't valid JSON or has a bad enum value.
	 *
	 * Spring would normally answer these with a 4xx, but the catch-all handler above sees them
	 * first, and it used to turn every one of them into a 500 plus an ERROR log line. That's wrong
	 * for the caller, and it pollutes the 5xx metrics an alert would be built on.
	 */
	private fun clientError(ex: Exception): Pair<HttpStatus, String>? = when (ex) {
		is ErrorResponse -> HttpStatus.resolve(ex.statusCode.value())
			?.takeIf { it.is4xxClientError }
			?.let { it to (ex.body.detail ?: it.reasonPhrase) }
		is MethodArgumentTypeMismatchException -> HttpStatus.BAD_REQUEST to "Invalid value for '${ex.name}'"
		is TypeMismatchException -> HttpStatus.BAD_REQUEST to "Invalid value for '${ex.propertyName}'"
		is HttpMessageNotReadableException -> HttpStatus.BAD_REQUEST to unreadableBodyMessage(ex)
		else -> null
	}

	/** Names the offending field when Jackson can tell us which one it was. */
	private fun unreadableBodyMessage(ex: HttpMessageNotReadableException): String {
		val field = (ex.cause as? MismatchedInputException)?.path
			?.joinToString(".") { it.fieldName ?: "[${it.index}]" }
			?.takeIf { it.isNotEmpty() }
		return if (field != null) "Invalid value for '$field'" else "Malformed request body"
	}

	private fun respond(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ApiError> =
		ResponseEntity.status(status).body(
			ApiError(
				timestamp = Instant.now(),
				status = status.value(),
				error = status.reasonPhrase,
				message = message,
				path = request.requestURI,
			),
		)
}
