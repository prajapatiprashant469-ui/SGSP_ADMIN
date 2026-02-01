package com.SGSP_ADMIN.invoice

data class ReceiverDto(
    val name: String,
    val address: String,
    val placeOfSupply: String? = null,
    val transportMode: String? = null,
    val state: String? = null,
    val stateCode: String? = null,
    val gstIn: String? = null
)

data class ConsigneeDto(
    val name: String,
    val address: String,
    val placeOfSupply: String? = null,
    val transportMode: String? = null,
    val state: String? = null,
    val code: String? = null
)


data class ItemDto(
    val serialNo: Int,
    val description: String,
    val hsnCode: String? = null,
    val quantity: Int,
    val rate: Double
) {
    val amount: Double
        get() = quantity * rate
}

data class TaxDto(
    val cgstPercent: Double = 0.0,
    val sgstPercent: Double = 0.0,
    val igstPercent: Double = 0.0
)

data class TotalSummaryDto(
    val totalBeforeTax: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val freightCharge: Double = 0.0,
    val totalAfterTax: Double
)

data class InvoiceRequest(
    val invoiceDate: String,
    val receiver: ReceiverDto,
    val consignee: ConsigneeDto,
    val items: List<ItemDto>,
    val tax: TaxDto,
    val freightCharge: Double = 0.0,
    val amountInWords: String? = null,
    val totalSummary: TotalSummaryDto
)
