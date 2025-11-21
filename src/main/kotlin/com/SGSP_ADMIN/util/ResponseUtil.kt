package com.SGSP_ADMIN.util

data class ErrorPayload(val code: String, val message: String?)
data class ApiResponse<T>(val success: Boolean, val data: T?, val error: ErrorPayload?)

fun <T> successPayload(data: T?): ApiResponse<Any?> =
    ApiResponse(success = true, data = data ?: linkedMapOf<String, Any?>(), error = null)

fun errorPayload(code: String, message: String?): ApiResponse<Any?> =
    ApiResponse(success = false, data = null, error = ErrorPayload(code, message))
