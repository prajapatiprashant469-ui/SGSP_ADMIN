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

        val titleFont = Font(Font.HELVETICA, 14f, Font.BOLD)
        val storeFont = Font(Font.HELVETICA, 16f, Font.BOLD)
        val bold = Font(Font.HELVETICA, 10f, Font.BOLD)
        val normal = Font(Font.HELVETICA, 10f, Font.NORMAL)
        val small = Font(Font.HELVETICA, 8f, Font.NORMAL)

        // HEADER
        val title = Paragraph("TAX INVOICE", titleFont)
        title.alignment = Element.ALIGN_CENTER
        doc.add(title)

        val storeName = Paragraph("SHASHANK SAREE CENTER", storeFont)
        storeName.alignment = Element.ALIGN_CENTER
        doc.add(storeName)

        val storeInfo = Paragraph("N9/28 A-1, Choti Patiya, Bajardihā, Varanasi-221109\nGSTIN: 09GHBPP6328G1Z2    Mobile: 8318325253", normal)
        storeInfo.alignment = Element.ALIGN_CENTER
        doc.add(storeInfo)

        doc.add(Chunk.NEWLINE)

        // Date (right)
        val dateStr = try {
            LocalDate.parse(req.invoiceDate).format(DateTimeFormatter.ISO_DATE)
        } catch (_: Exception) {
            req.invoiceDate
        }
        val datePara = Paragraph("Date: $dateStr", normal)
        datePara.alignment = Element.ALIGN_RIGHT
        doc.add(datePara)

        doc.add(Chunk.NEWLINE)

        // TWO-COLUMN: Receiver (left) and Consignee (right)
        val twoCol = PdfPTable(2)
        twoCol.widthPercentage = 100f
        twoCol.setWidths(floatArrayOf(50f, 50f))

        fun titledCell(titleText: String, content: String?): PdfPCell {
            val p = Phrase()
            p.add(Chunk("$titleText\n", bold))
            p.add(Chunk(content ?: "", normal))
            val cell = PdfPCell(Phrase(p))
            cell.setBorder(PdfPCell.NO_BORDER)
            return cell
        }

        val r = req.receiver
        val receiverTable = PdfPTable(1)
        receiverTable.widthPercentage = 100f
        receiverTable.addCell(titledCell("RECEIVER (BILLED TO)", ""))
        receiverTable.addCell(titledCell("Name", r.name))
        receiverTable.addCell(titledCell("Address", r.address))
        receiverTable.addCell(titledCell("Place of Supply", r.placeOfSupply ?: ""))
        receiverTable.addCell(titledCell("Transport Mode", r.transportMode ?: ""))
        receiverTable.addCell(titledCell("State", r.state ?: ""))
        receiverTable.addCell(titledCell("State Code", r.stateCode ?: ""))
        receiverTable.addCell(titledCell("GSTIN", r.gstIn ?: ""))

        val rc = PdfPCell(receiverTable)
        rc.setBorder(PdfPCell.NO_BORDER)
        twoCol.addCell(rc)

        val c = req.consignee
        val consigneeTable = PdfPTable(1)
        consigneeTable.widthPercentage = 100f
        consigneeTable.addCell(titledCell("CONSIGNEE (SHIPPED TO)", ""))
        consigneeTable.addCell(titledCell("Name", c.name))
        consigneeTable.addCell(titledCell("Address", c.address))
        consigneeTable.addCell(titledCell("Place of Supply", c.placeOfSupply ?: ""))
        consigneeTable.addCell(titledCell("Transport Mode", c.transportMode ?: ""))
        consigneeTable.addCell(titledCell("Invoice No", c.invoiceNo ?: ""))
        consigneeTable.addCell(titledCell("State", c.state ?: "U.P."))
        consigneeTable.addCell(titledCell("Code", c.code ?: "09"))

        val cc = PdfPCell(consigneeTable)
        cc.setBorder(PdfPCell.NO_BORDER)
        twoCol.addCell(cc)

        doc.add(twoCol)
        doc.add(Chunk.NEWLINE)

        // ITEM TABLE
        val itemTable = PdfPTable(6)
        itemTable.widthPercentage = 100f
        itemTable.setWidths(floatArrayOf(6f, 40f, 12f, 8f, 12f, 12f))

        fun headerCell(text: String): PdfPCell {
            val c = PdfPCell(Phrase(text, bold))
            c.setHorizontalAlignment(Element.ALIGN_CENTER)
            c.setVerticalAlignment(Element.ALIGN_MIDDLE)
            c.setBackgroundColor(Color(230, 230, 230))
            c.setPadding(6f)
            return c
        }

        itemTable.addCell(headerCell("S.No"))
        itemTable.addCell(headerCell("Description"))
        itemTable.addCell(headerCell("HSN"))
        itemTable.addCell(headerCell("Qty"))
        itemTable.addCell(headerCell("Rate"))
        itemTable.addCell(headerCell("Amount"))

        req.items.forEach { it ->
            itemTable.addCell(PdfPCell(Phrase(it.serialNo.toString(), normal)).apply { setHorizontalAlignment(Element.ALIGN_CENTER); setPadding(6f) })
            itemTable.addCell(PdfPCell(Phrase(it.description, normal)).apply { setPadding(6f) })
            itemTable.addCell(PdfPCell(Phrase(it.hsnCode ?: "", normal)).apply { setHorizontalAlignment(Element.ALIGN_CENTER); setPadding(6f) })
            itemTable.addCell(PdfPCell(Phrase(it.quantity.toString(), normal)).apply { setHorizontalAlignment(Element.ALIGN_CENTER); setPadding(6f) })
            itemTable.addCell(PdfPCell(Phrase(String.format("%.2f", it.rate), normal)).apply { setHorizontalAlignment(Element.ALIGN_RIGHT); setPadding(6f) })
            itemTable.addCell(PdfPCell(Phrase(String.format("%.2f", it.amount), normal)).apply { setHorizontalAlignment(Element.ALIGN_RIGHT); setPadding(6f) })
        }

        doc.add(itemTable)
        doc.add(Chunk.NEWLINE)

        // SUMMARY + TAX DETAILS (right aligned)
        val summaryTable = PdfPTable(2)
        summaryTable.horizontalAlignment = Element.ALIGN_RIGHT
        summaryTable.widthPercentage = 50f
        summaryTable.setWidths(floatArrayOf(60f, 40f))

        fun addSummaryRow(label: String, value: String) {
            val lCell = PdfPCell(Phrase(label, normal))
            lCell.setBorder(PdfPCell.NO_BORDER)
            lCell.setHorizontalAlignment(Element.ALIGN_LEFT)
            val vCell = PdfPCell(Phrase(value, normal))
            vCell.setBorder(PdfPCell.NO_BORDER)
            vCell.setHorizontalAlignment(Element.ALIGN_RIGHT)
            summaryTable.addCell(lCell)
            summaryTable.addCell(vCell)
        }

        addSummaryRow("Total before tax", String.format("%.2f", req.totalSummary.totalBeforeTax))
        addSummaryRow("CGST (${req.tax.cgstPercent}%)", String.format("%.2f", req.totalSummary.cgstAmount))
        addSummaryRow("SGST (${req.tax.sgstPercent}%)", String.format("%.2f", req.totalSummary.sgstAmount))
        addSummaryRow("IGST (${req.tax.igstPercent}%)", String.format("%.2f", req.totalSummary.igstAmount))
        addSummaryRow("Freight Charge", String.format("%.2f", req.totalSummary.freightCharge))
        addSummaryRow("Total After Tax", String.format("%.2f", req.totalSummary.totalAfterTax))
        addSummaryRow("Amount (in words)", req.amountInWords ?: NumberToWords.convert(req.totalSummary.totalAfterTax))

        doc.add(summaryTable)
        doc.add(Chunk.NEWLINE)

        // BANK DETAILS
        val bankTable = PdfPTable(1)
        bankTable.widthPercentage = 100f
        val bankCell = PdfPCell()
        bankCell.setBorder(PdfPCell.NO_BORDER)
        val bankPara = Paragraph()
        bankPara.add(Chunk("BANK DETAILS\n", bold))
        bankPara.add(Chunk("Bank Name: FEDERAL BANK\nA/C No: 15950200009321\nBranch: Mahmoorganj, Varanasi\nIFSC: FDRL0001595\n", normal))
        bankCell.addElement(bankPara)
        bankTable.addCell(bankCell)
        doc.add(bankTable)
        doc.add(Chunk.NEWLINE)

        // FOOTER
        val footer = Paragraph()
        footer.add(Chunk("Interest will be charged on overdue invoices as per applicable law.\n", small))
        footer.add(Chunk("Jurisdiction: Varanasi Courts\n", small))
        footer.add(Chunk("\"Goods once sold will not be taken back or exchanged\"\n\n", bold))
        footer.add(Chunk("\n\n\nAuthorized Signatory\n\n_________________________", normal))
        footer.alignment = Element.ALIGN_LEFT
        doc.add(footer)

        doc.close()
        writer.close()
        return baos.toByteArray()
    }
}
