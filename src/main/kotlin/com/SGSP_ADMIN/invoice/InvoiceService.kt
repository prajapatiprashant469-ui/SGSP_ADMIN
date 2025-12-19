package com.SGSP_ADMIN.invoice

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import java.awt.Color
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class InvoiceService {

    fun generatePdf(req: InvoiceRequest): ByteArray {
        val baos = ByteArrayOutputStream()
        val doc = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        val writer = PdfWriter.getInstance(doc, baos)
        doc.open()

        val bold = Font(Font.HELVETICA, 9f, Font.BOLD)
        val normal = Font(Font.HELVETICA, 9f)
        val small = Font(Font.HELVETICA, 8f)

        /* ================= OUTER BORDER ================= */
        val cb = writer.directContent
        val rect = Rectangle(doc.left(), doc.bottom(), doc.right(), doc.top())
        rect.border = Rectangle.BOX
        rect.borderWidth = 1.5f
        cb.rectangle(rect)

        /* ================= HEADER ================= */
        val header = PdfPTable(1)
        header.widthPercentage = 100f

        fun center(text: String, font: Font) =
            PdfPCell(Phrase(text, font)).apply {
                border = PdfPCell.NO_BORDER
                horizontalAlignment = Element.ALIGN_CENTER
                setPadding(3f)
            }

        header.addCell(center("TAX INVOICE", Font(Font.HELVETICA, 10f, Font.BOLD)))
        header.addCell(center("SHASHANK SAREE CENTER", Font(Font.HELVETICA, 16f, Font.BOLD)))
        header.addCell(center("N9/28 A-1, Choti Patiya, Bajardihā, Varanasi-221109", normal))
        header.addCell(center("GSTIN - 09GHBPP6328G1Z2", bold))
        header.addCell(center("Mob: 8318325253", normal))

        doc.add(header)
        doc.add(Chunk.NEWLINE)

        val datePara = Paragraph("Date: ${req.invoiceDate}", normal)
        datePara.alignment = Element.ALIGN_RIGHT
        doc.add(datePara)

        doc.add(Chunk.NEWLINE)

        /* ================= RECEIVER / CONSIGNEE ================= */
        val partyTable = PdfPTable(2)
        partyTable.widthPercentage = 100f
        partyTable.setWidths(floatArrayOf(50f, 50f))

        fun sectionHeader(text: String) =
            PdfPCell(Phrase(text, bold)).apply {
                backgroundColor = Color(220, 220, 220)
                setPadding(5f)
            }

        fun boxed(label: String, value: String?) =
            PdfPCell(Phrase("$label : ${value ?: ""}", normal)).apply {
                setPadding(5f)
            }

        partyTable.addCell(sectionHeader("DETAILS OF RECEIVER (BILLED TO)"))
        partyTable.addCell(sectionHeader("DETAILS OF CONSIGNEE (SHIPPED TO)"))

        val r = req.receiver
        val c = req.consignee

        partyTable.addCell(boxed("Name", r.name))
        partyTable.addCell(boxed("Name", c.name))

        partyTable.addCell(boxed("Address", r.address))
        partyTable.addCell(boxed("Address", c.address))

        partyTable.addCell(boxed("Place of Supply", r.placeOfSupply))
        partyTable.addCell(boxed("Place of Supply", c.placeOfSupply))

        partyTable.addCell(boxed("Transport Mode", r.transportMode))
        partyTable.addCell(boxed("Transport Mode", c.transportMode))

        partyTable.addCell(boxed("State", r.state))
        partyTable.addCell(boxed("State / Code", "${c.state} / ${c.code}"))

        partyTable.addCell(boxed("GSTIN", r.gstIn))
        partyTable.addCell(boxed("Invoice No", c.invoiceNo))

        doc.add(partyTable)
        doc.add(Chunk.NEWLINE)

        /* ================= ITEMS ================= */
        val itemTable = PdfPTable(7)
        itemTable.widthPercentage = 100f
        itemTable.setWidths(floatArrayOf(5f, 35f, 10f, 10f, 10f, 15f, 15f))

        fun headerCell(text: String) =
            PdfPCell(Phrase(text, Font(Font.HELVETICA, 9f, Font.BOLD, Color.WHITE))).apply {
                backgroundColor = Color(40, 40, 40)
                horizontalAlignment = Element.ALIGN_CENTER
                setPadding(5f)
            }

        listOf(
            "S.No", "Description of Goods", "HSN Code",
            "Quant.", "Rate", "Rs.", "Amount"
        ).forEach { itemTable.addCell(headerCell(it)) }

        req.items.forEach {
            itemTable.addCell(PdfPCell(Phrase(it.serialNo.toString(), normal)))
            itemTable.addCell(PdfPCell(Phrase(it.description, normal)))
            itemTable.addCell(PdfPCell(Phrase(it.hsnCode ?: "", normal)))
            itemTable.addCell(PdfPCell(Phrase(it.quantity.toString(), normal)))
            itemTable.addCell(PdfPCell(Phrase("%.2f".format(it.rate), normal)))
            itemTable.addCell(PdfPCell(Phrase("", normal)))
            itemTable.addCell(PdfPCell(Phrase("%.2f".format(it.amount), normal)))
        }

        repeat(15 - req.items.size) {
            repeat(7) {
                itemTable.addCell(PdfPCell(Phrase("")).apply { fixedHeight = 18f })
            }
        }

        doc.add(itemTable)
        doc.add(Chunk.NEWLINE)

        /* ================= BANK + SUMMARY ================= */
        val bottom = PdfPTable(2)
        bottom.widthPercentage = 100f
        bottom.setWidths(floatArrayOf(55f, 45f))

        val bank = PdfPCell(Phrase(
            "Bank Details:\n" +
                    "Bank Name: FEDERAL BANK\n" +
                    "A/c No: 15950200009321\n" +
                    "Branch: Mahmoorganj, Varanasi\n" +
                    "IFSC Code: FDRL0001595",
            normal
        ))
        bank.setPadding(5f)
        bottom.addCell(bank)

        val summary = PdfPTable(2)
        summary.widthPercentage = 100f
        summary.setWidths(floatArrayOf(65f, 35f))

        fun sumRow(l: String, v: String) {
            summary.addCell(PdfPCell(Phrase(l, normal)))
            summary.addCell(PdfPCell(Phrase(v, normal)).apply {
                horizontalAlignment = Element.ALIGN_RIGHT
            })
        }

        sumRow("Total Amount Before Tax", "%.2f".format(req.totalSummary.totalBeforeTax))
        sumRow("Add: CGST", "%.2f".format(req.totalSummary.cgstAmount))
        sumRow("Add: SGST", "%.2f".format(req.totalSummary.sgstAmount))
        sumRow("Add: IGST", "%.2f".format(req.totalSummary.igstAmount))
        sumRow("Freight Charge", "%.2f".format(req.totalSummary.freightCharge))
        sumRow("Total Amount After Tax", "%.2f".format(req.totalSummary.totalAfterTax))

        bottom.addCell(PdfPCell(summary))
        doc.add(bottom)

        doc.add(Chunk.NEWLINE)

        /* ================= FOOTER ================= */
        val footerTable = PdfPTable(2)
        footerTable.widthPercentage = 100f
        footerTable.setWidths(floatArrayOf(70f, 30f))

        val leftFooter = Paragraph(
            "• Interest will be charged 18% P/A on unpaid balance if not paid within 15 days.\n" +
                    "• All disputes subject to Varanasi Jurisdiction only.\n" +
                    "• Goods once sold will not be taken back or exchanged.\n\n"+
                    "For: SHASHANK SAREE CENTER",
            small
        )
        val leftCell = PdfPCell()
        leftCell.border = PdfPCell.NO_BORDER
        leftCell.addElement(leftFooter)
        footerTable.addCell(leftCell)

        val rightPara = Paragraph()
        val sig = Paragraph("Authorised Signatory", normal)
        sig.alignment = Element.ALIGN_RIGHT
        rightPara.add(sig)
        rightPara.alignment = Element.ALIGN_RIGHT

        val rightCell = PdfPCell()
        rightCell.border = PdfPCell.NO_BORDER
        rightCell.addElement(rightPara)
        rightCell.horizontalAlignment = Element.ALIGN_RIGHT
        footerTable.addCell(rightCell)

        doc.add(footerTable)

        doc.close()
        writer.close()
        return baos.toByteArray()
    }

}
