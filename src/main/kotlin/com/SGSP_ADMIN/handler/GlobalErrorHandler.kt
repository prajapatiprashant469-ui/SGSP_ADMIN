package com.SGSP_ADMIN.handler

import com.SGSP_ADMIN.util.ApiResponse
import com.SGSP_ADMIN.util.errorPayload
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalErrorHandler {
    @ExceptionHandler(Exception::class)
    fun handle(e: Exception): ResponseEntity<ApiResponse<Any?>> {
        val code = if (e is ApiException) e.code else "INTERNAL_ERROR"
        val msg = e.message ?: "Internal server error"
        val status = if (e is ApiException) e.status else HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status).body(errorPayload(code, msg))
    }
}

class ApiException(val code: String, override val message: String?, val status: HttpStatus = HttpStatus.BAD_REQUEST) : RuntimeException(message)
