package com.forgeflow.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
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

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
		log.error("Unhandled exception while processing {} {}", request.method, request.requestURI, ex)
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request)
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
