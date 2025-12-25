package com.SGSP_ADMIN.invoice

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "invoice_sequence")
data class InvoiceSequence(
    @Id
    val id: String = "INVOICE",
    val lastInvoiceNo: Long = 0
)
