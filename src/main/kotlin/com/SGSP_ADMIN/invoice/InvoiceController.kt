package com.SGSP_ADMIN.invoice

// ...existing imports...
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Paths

@RestController
@RequestMapping("/api/invoice")
class InvoiceController(
    private val service: InvoiceService,
    private val mapper: ObjectMapper
) {

    // accept JSON bodies and also plain text (so we can inspect raw body and give helpful errors)
    @PostMapping("/generate", consumes = [MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE])
    fun generate(@RequestBody body: String): ResponseEntity<Any> {
        val trimmed = body.trim()

        // If client sent a file reference like "@invoice.json", try to read it from server FS (only when available).
        if (trimmed.startsWith("@")) {
            val ref = trimmed.substring(1).trim()
            val path = Paths.get(ref)
            if (Files.exists(path)) {
                val fileContent = try {
                    String(Files.readAllBytes(path))
                } catch (ex: Exception) {
                    val msg = "Failed to read server-side file '$ref': ${ex.message ?: "error"}"
                    val err = mapOf("success" to false, "data" to null, "error" to mapOf("code" to "INVALID_REQUEST", "message" to msg))
                    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(err)
                }
                // replace body with file content for parsing below
                return try {
                    val req = mapper.readValue(fileContent, InvoiceRequest::class.java)
                    val pdf = service.generatePdf(req)
                    val headers = HttpHeaders()
                    headers.contentType = MediaType.APPLICATION_PDF
                    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"")
                    ResponseEntity.ok().headers(headers).body(pdf)
                } catch (ex: Exception) {
                    val msg = "Failed to parse JSON from server-side file '$ref': ${ex.message ?: "invalid JSON"}"
                    val err = mapOf("success" to false, "data" to null, "error" to mapOf("code" to "INVALID_JSON", "message" to msg))
                    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(err)
                }
            } else {
                val msg = "Request body looks like a client-side file reference ('$trimmed'). " +
                    "When using curl to send a local file, do NOT quote the @. Example:\n\n" +
                    "  curl -X POST http://localhost:8080/api/invoice/generate \\\n" +
                    "    -H 'Content-Type: application/json' --data-binary @invoice.json -o invoice.pdf\n\n" +
                    "You sent the literal string starting with '@' so the server couldn't read JSON. " +
                    "Server-side file '$ref' not found."
                val err = mapOf("success" to false, "data" to null, "error" to mapOf("code" to "INVALID_REQUEST", "message" to msg))
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(err)
            }
        }

        // Normal flow: parse JSON sent in body
        val req: InvoiceRequest = try {
            mapper.readValue(body, InvoiceRequest::class.java)
        } catch (ex: Exception) {
            val msg = "Failed to parse JSON body: ${ex.message ?: "invalid JSON"}"
            val err = mapOf("success" to false, "data" to null, "error" to mapOf("code" to "INVALID_JSON", "message" to msg))
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(err)
        }

        // generate PDF
        return try {
            val pdf = service.generatePdf(req)
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_PDF
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"")
            ResponseEntity.ok().headers(headers).body(pdf)
        } catch (ex: Exception) {
            val err = mapOf("success" to false, "data" to null, "error" to mapOf("code" to "INTERNAL_ERROR", "message" to (ex.message ?: "Failed to generate PDF")))
            ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body(err)
        }
    }
}
