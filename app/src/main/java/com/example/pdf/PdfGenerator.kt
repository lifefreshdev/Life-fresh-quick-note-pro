package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.database.LeadEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    /**
     * Generates a structural CRM Report PDF and stores it in the app's cache directory.
     * Returns the generated file handle.
     */
    fun generateCrmReport(context: Context, leads: List<LeadEntity>): File {
        val pdfDocument = PdfDocument()

        // Page sizes: standard A4 (595 x 842 pixels / postscript points)
        val pageWidth = 595
        val pageHeight = 842

        // List categories
        val pendingLeads = leads.filter { it.status.equals("Pending", ignoreCase = true) }
        val completeLeads = leads.filter { it.status.equals("Complete", ignoreCase = true) }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // LifeFresh Theme Palette
        val lifeFreshGreen = Color.rgb(46, 125, 50) // #2E7D32 Primary LifeFresh Green
        val pendingAmberOrange = Color.rgb(217, 119, 6) // #D97706 Professional Amber/Orange
        val lifeFreshDarkText = Color.rgb(25, 29, 25) // #191D19 Dark legible onBackground text
        val lifeFreshMeta = Color.rgb(85, 105, 85) // Slate-forest neutral for secondary info
        val lifeFreshBorder = Color.rgb(220, 232, 222) // Subtle soft green/gray border
        val lifeFreshZebra = Color.rgb(246, 251, 246) // #F6FBF6 Subtle light green tint row

        // Paint preparations
        val titlePaint = Paint().apply {
            color = lifeFreshGreen
            textSize = 20f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val metaPaint = Paint().apply {
            color = lifeFreshMeta
            textSize = 10f
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = lifeFreshGreen
            textSize = 14f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val headBgPaint = Paint().apply {
            color = lifeFreshGreen
            style = Paint.Style.FILL
        }

        val headTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val rowTextPaint = Paint().apply {
            color = lifeFreshDarkText
            textSize = 9f
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = lifeFreshBorder
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val zebraPaint = Paint().apply {
            color = lifeFreshZebra
            style = Paint.Style.FILL
        }

        var yPos = 40f
        val xName = 30f
        val xMobile = 150f
        val xRelation = 270f
        val xDiseases = 390f
        val rowHeight = 24f

        fun drawHeader(canvas: Canvas, pageNum: Int) {
            canvas.drawText("LifeFresh Quick Note Pro - CRM Report", 30f, 35f, titlePaint)
            val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.getDefault())
            canvas.drawText("Generated on: ${sdf.format(Date())}", 30f, 50f, metaPaint)
            val activeCount = leads.filter { !it.archived }.size
            canvas.drawText("Total Active Leads: $activeCount", 30f, 62f, metaPaint)
            canvas.drawLine(30f, 75f, 565f, 75f, borderPaint)
        }

        fun drawFooter(canvas: Canvas, pageNum: Int) {
            val footerPaint = Paint().apply {
                color = Color.rgb(130, 150, 132)
                textSize = 8f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Page $pageNum", (pageWidth / 2).toFloat(), (pageHeight - 20).toFloat(), footerPaint)
        }

        // Initialize first page header
        drawHeader(canvas, pageNumber)
        yPos = 95f

        fun checkNewPage(neededHeight: Float = rowHeight, isInsideTable: Boolean = false, headerColor: Int = lifeFreshGreen) {
            if (yPos + neededHeight > pageHeight - 40f) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                drawHeader(canvas, pageNumber)
                yPos = 95f

                if (isInsideTable) {
                    headBgPaint.color = headerColor
                    canvas.drawRect(30f, yPos, 565f, yPos + rowHeight, headBgPaint)
                    canvas.drawText("Name", xName + 6f, yPos + 16f, headTextPaint)
                    canvas.drawText("Mobile", xMobile + 6f, yPos + 16f, headTextPaint)
                    canvas.drawText("Relation", xRelation + 6f, yPos + 16f, headTextPaint)
                    canvas.drawText("Disease Issues", xDiseases + 6f, yPos + 16f, headTextPaint)
                    yPos += rowHeight
                }
            }
        }

        // Draw Pending section
        if (pendingLeads.isNotEmpty()) {
            checkNewPage(50f)
            sectionPaint.color = pendingAmberOrange
            canvas.drawText("Pending Customers", 30f, yPos, sectionPaint)
            yPos += 10f

            checkNewPage(rowHeight)
            // Table Header block
            headBgPaint.color = pendingAmberOrange
            canvas.drawRect(30f, yPos, 565f, yPos + rowHeight, headBgPaint)
            canvas.drawText("Name", xName + 6f, yPos + 16f, headTextPaint)
            canvas.drawText("Mobile", xMobile + 6f, yPos + 16f, headTextPaint)
            canvas.drawText("Relation", xRelation + 6f, yPos + 16f, headTextPaint)
            canvas.drawText("Disease Issues", xDiseases + 6f, yPos + 16f, headTextPaint)
            yPos += rowHeight

            pendingLeads.forEachIndexed { idx, lead ->
                // Parse disease lists completely without truncation
                val diseasesStr = lead.diseases.trim()
                    .removePrefix("[")
                    .removeSuffix("]")
                    .replace("\"", "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ") { d -> if (d == "Other" && lead.otherDisease.isNotEmpty()) lead.otherDisease else d }

                val diseaseLines = wrapText(diseasesStr.ifEmpty { "-" }, rowTextPaint, 163f)
                val lineSpacing = 11.5f
                val currentRowHeight = maxOf(rowHeight, diseaseLines.size * lineSpacing + 12f)

                checkNewPage(currentRowHeight, isInsideTable = true, headerColor = pendingAmberOrange)

                // Zebra row stripe background
                if (idx % 2 == 1) {
                    canvas.drawRect(30f, yPos, 565f, yPos + currentRowHeight, zebraPaint)
                }
                // Grid cell borders
                canvas.drawRect(30f, yPos, 565f, yPos + currentRowHeight, borderPaint)
                canvas.drawLine(xMobile, yPos, xMobile, yPos + currentRowHeight, borderPaint)
                canvas.drawLine(xRelation, yPos, xRelation, yPos + currentRowHeight, borderPaint)
                canvas.drawLine(xDiseases, yPos, xDiseases, yPos + currentRowHeight, borderPaint)

                // Populate cell texts
                canvas.drawText(truncateText(lead.name, 22), xName + 6f, yPos + 15f, rowTextPaint)
                canvas.drawText(truncateText(lead.mobile, 15), xMobile + 6f, yPos + 15f, rowTextPaint)
                
                val relText = if (lead.relation == "Other") "Other (${lead.otherRelation})" else lead.relation
                canvas.drawText(truncateText(relText, 22), xRelation + 6f, yPos + 15f, rowTextPaint)

                // Draw all disease lines without truncation
                diseaseLines.forEachIndexed { lineIdx, lineText ->
                    canvas.drawText(lineText, xDiseases + 6f, yPos + 15f + (lineIdx * lineSpacing), rowTextPaint)
                }

                yPos += currentRowHeight
            }
            yPos += 20f // space between blocks
        }

        // Draw Complete section
        if (completeLeads.isNotEmpty()) {
            checkNewPage(50f)
            sectionPaint.color = lifeFreshGreen
            canvas.drawText("Complete Customers", 30f, yPos, sectionPaint)
            yPos += 10f

            checkNewPage(rowHeight)
            // Table Header block
            headBgPaint.color = lifeFreshGreen
            canvas.drawRect(30f, yPos, 565f, yPos + rowHeight, headBgPaint)
            canvas.drawText("Name", xName + 6f, yPos + 16f, headTextPaint)
            canvas.drawText("Mobile", xMobile + 6f, yPos + 16f, headTextPaint)
            canvas.drawText("Relation", xRelation + 6f, yPos + 16f, headTextPaint)
            canvas.drawText("Disease Issues", xDiseases + 6f, yPos + 16f, headTextPaint)
            yPos += rowHeight

            completeLeads.forEachIndexed { idx, lead ->
                // Parse disease lists completely without truncation
                val diseasesStr = lead.diseases.trim()
                    .removePrefix("[")
                    .removeSuffix("]")
                    .replace("\"", "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ") { d -> if (d == "Other" && lead.otherDisease.isNotEmpty()) lead.otherDisease else d }

                val diseaseLines = wrapText(diseasesStr.ifEmpty { "-" }, rowTextPaint, 163f)
                val lineSpacing = 11.5f
                val currentRowHeight = maxOf(rowHeight, diseaseLines.size * lineSpacing + 12f)

                checkNewPage(currentRowHeight, isInsideTable = true, headerColor = lifeFreshGreen)

                // Zebra row stripe background
                if (idx % 2 == 1) {
                    canvas.drawRect(30f, yPos, 565f, yPos + currentRowHeight, zebraPaint)
                }
                // Grid cell borders
                canvas.drawRect(30f, yPos, 565f, yPos + currentRowHeight, borderPaint)
                canvas.drawLine(xMobile, yPos, xMobile, yPos + currentRowHeight, borderPaint)
                canvas.drawLine(xRelation, yPos, xRelation, yPos + currentRowHeight, borderPaint)
                canvas.drawLine(xDiseases, yPos, xDiseases, yPos + currentRowHeight, borderPaint)

                // Populate cells
                canvas.drawText(truncateText(lead.name, 22), xName + 6f, yPos + 15f, rowTextPaint)
                canvas.drawText(truncateText(lead.mobile, 15), xMobile + 6f, yPos + 15f, rowTextPaint)

                val relText = if (lead.relation == "Other") "Other (${lead.otherRelation})" else lead.relation
                canvas.drawText(truncateText(relText, 22), xRelation + 6f, yPos + 15f, rowTextPaint)

                // Draw all disease lines without truncation
                diseaseLines.forEachIndexed { lineIdx, lineText ->
                    canvas.drawText(lineText, xDiseases + 6f, yPos + 15f + (lineIdx * lineSpacing), rowTextPaint)
                }

                yPos += currentRowHeight
            }
        }

        // Draw last page footer and finish
        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        // Write output stream to file
        val file = File(context.cacheDir, "LifeFresh_CRM_Report.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("-")

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (word.isEmpty()) continue
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                if (paint.measureText(word) <= maxWidth) {
                    currentLine.append(word)
                } else {
                    // Break long words if necessary
                    var remaining = word
                    val measured = FloatArray(1)
                    while (remaining.isNotEmpty()) {
                        var count = paint.breakText(remaining, true, maxWidth, measured)
                        if (count <= 0) count = 1
                        val chunk = remaining.substring(0, count)
                        remaining = remaining.substring(count)
                        if (remaining.isNotEmpty()) {
                            lines.add(chunk)
                        } else {
                            currentLine.append(chunk)
                        }
                    }
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.isEmpty()) listOf("-") else lines
    }

    private fun truncateText(text: String, maxLen: Int): String {
        return if (text.length > maxLen) {
            text.substring(0, maxLen - 2) + ".."
        } else {
            text
        }
    }
}

