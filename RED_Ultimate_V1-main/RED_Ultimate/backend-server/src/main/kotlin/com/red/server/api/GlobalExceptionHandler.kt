package com.red.server.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    private val rateLimitCounts = ConcurrentHashMap<String, Int>()

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.allErrors.associate { 
            val fieldName = (it as FieldError).field
            val errorMessage = it.defaultMessage
            fieldName to (errorMessage ?: "Invalid value")
        }
        
        val body = mapOf(
            "error" to "VALIDATION_FAILED",
            "message" to "Invalid request payload",
            "details" to errors,
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity(body, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<Map<String, Any>> {
        val body = mapOf(
            "error" to ex.statusCode.toString(),
            "message" to (ex.reason ?: "Error occurred"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity(body, ex.statusCode)
    }
    
    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<Map<String, Any>> {
        log.error("Unhandled exception: ", ex)
        
        val body = mapOf(
            "error" to "INTERNAL_SERVER_ERROR",
            "message" to "An unexpected error occurred. Please try again later.",
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity(body, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    // Rate Limiting simulation/handler logic
    // If a specific RateLimitException is thrown, we can catch it here.
    // For now, providing a generic structure.
}
