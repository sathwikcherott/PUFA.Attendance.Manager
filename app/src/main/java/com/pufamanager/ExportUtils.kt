package com.pufamanager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Payment
import com.pufamanager.data.entity.Player
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 50f

    fun generatePlayerListPdf(context: Context, batch: Batch, players: List<Player>): File {
        val sortedPlayers = players.sortedBy { it.name }
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = MARGIN

        // Header
        y = drawHeader(canvas, paint, "Player List", y)
        
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        canvas.drawText("Batch: ${batch.name}", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Total Players: ${sortedPlayers.size}", MARGIN, y, paint)
        y += 20f
        val exportDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Export Date: $exportDate", MARGIN, y, paint)
        y += 40f

        // Table Header
        val nameColWidth = 350f
        val rowHeight = 25f
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, paint)
        canvas.drawLine(MARGIN + nameColWidth, y, MARGIN + nameColWidth, y + rowHeight, paint)

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("Player Name", MARGIN + 10f, y + 18f, paint)
        canvas.drawText("YOB", MARGIN + nameColWidth + 10f, y + 18f, paint)
        y += rowHeight

        // Table Rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        sortedPlayers.forEach { player ->
            if (y > PAGE_HEIGHT - MARGIN - rowHeight) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
            
            paint.style = Paint.Style.STROKE
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, paint)
            canvas.drawLine(MARGIN + nameColWidth, y, MARGIN + nameColWidth, y + rowHeight, paint)
            
            paint.style = Paint.Style.FILL
            canvas.drawText(player.name, MARGIN + 10f, y + 18f, paint)
            canvas.drawText(player.yearOfBirth.toString(), MARGIN + nameColWidth + 10f, y + 18f, paint)
            y += rowHeight
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "${batch.name.replace(" ", "_")}_PlayerList.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    fun generatePaymentReportPdf(
        context: Context,
        batch: Batch,
        month: String,
        players: List<Player>,
        payments: List<Payment>
    ): File? {
        val batchPlayers = players.filter { it.batchId == batch.id }
        val monthPayments = payments.filter { it.month == month }
        
        val paidPlayers = batchPlayers.filter { p -> monthPayments.any { it.playerId == p.id } }.sortedBy { it.name }
        val exemptPlayers = batchPlayers.filter { it.isExempted }.sortedBy { it.name }
        val unpaidPlayers = batchPlayers.filter { !it.isExempted && monthPayments.none { pay -> pay.playerId == it.id } }.sortedBy { it.name }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = MARGIN

        y = drawHeader(canvas, paint, "Payment Report", y)
        
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        canvas.drawText("Batch: ${batch.name}", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Month: $month", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Summary: ${paidPlayers.size} Paid, ${unpaidPlayers.size} Unpaid, ${exemptPlayers.size} Exempt", MARGIN, y, paint)
        y += 40f

        fun drawSection(title: String, list: List<Player>) {
            if (y > PAGE_HEIGHT - MARGIN - 60f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
            
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14f
            canvas.drawText(title, MARGIN, y, paint)
            y += 25f
            
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 12f
            if (list.isEmpty()) {
                canvas.drawText("None", MARGIN + 20f, y, paint)
                y += 20f
            } else {
                list.forEach { player ->
                    if (y > PAGE_HEIGHT - MARGIN - 20f) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN
                    }
                    canvas.drawText("• ${player.name}", MARGIN + 20f, y, paint)
                    y += 20f
                }
            }
            y += 15f
        }

        drawSection("PAID PLAYERS", paidPlayers)
        drawSection("UNPAID PLAYERS", unpaidPlayers)
        drawSection("EXEMPT PLAYERS", exemptPlayers)

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "${batch.name.replace(" ", "_")}_PaymentReport_${month.replace(" ", "_")}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    fun generateAttendanceSummaryPdf(
        context: Context,
        batch: Batch,
        month: String,
        players: List<Player>,
        attendanceList: List<Attendance>
    ): File? {
        val batchPlayers = players.filter { it.batchId == batch.id }.sortedBy { it.name }
        val monthPrefix = monthToDatePrefix(month)
        val monthAttendance = attendanceList.filter { it.date.startsWith(monthPrefix) }
        val dates = monthAttendance.map { it.date }.distinct().sorted()

        if (dates.isEmpty()) return null

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = MARGIN

        y = drawHeader(canvas, paint, "Attendance Summary Report", y)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        canvas.drawText("Batch: ${batch.name}", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Month: $month", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Total Attendance Days: ${dates.size}", MARGIN, y, paint)
        y += 40f

        // Table setup
        val availableWidth = PAGE_WIDTH - 2 * MARGIN
        val nameColWidth = 140f
        val totalColWidth = 40f
        val dateColWidth = (availableWidth - nameColWidth - totalColWidth) / dates.size.coerceAtLeast(1)
        val rowHeight = 25f

        // Draw Table Header
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, paint)
        
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("Player", MARGIN + 5f, y + 17f, paint)
        
        dates.forEachIndexed { index, date ->
            val dateNum = date.substring(8) // "yyyy-MM-dd" -> "dd"
            val x = MARGIN + nameColWidth + (index * dateColWidth)
            canvas.drawText(dateNum, x + (dateColWidth / 2) - 5f, y + 17f, paint)
        }
        canvas.drawText("Total", MARGIN + nameColWidth + (dates.size * dateColWidth) + 5f, y + 17f, paint)
        
        y += rowHeight

        // Draw Player Rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        batchPlayers.forEach { player ->
            if (y > PAGE_HEIGHT - MARGIN - rowHeight) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }

            paint.style = Paint.Style.STROKE
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, paint)
            
            paint.style = Paint.Style.FILL
            val displayName = if (player.name.length > 20) player.name.take(17) + "..." else player.name
            canvas.drawText(displayName, MARGIN + 5f, y + 17f, paint)

            var presentCount = 0
            dates.forEachIndexed { index, date ->
                val att = monthAttendance.find { it.playerId == player.id && it.date == date }
                val status = when (att?.isPresent) {
                    true -> {
                        presentCount++
                        "P"
                    }
                    false -> "A"
                    else -> "-"
                }
                val x = MARGIN + nameColWidth + (index * dateColWidth)
                canvas.drawText(status, x + (dateColWidth / 2) - 5f, y + 17f, paint)
            }
            canvas.drawText(presentCount.toString(), MARGIN + nameColWidth + (dates.size * dateColWidth) + 15f, y + 17f, paint)
            
            y += rowHeight
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "${batch.name.replace(" ", "_")}_Attendance_Summary_${month.replace(" ", "_")}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, subtitle: String, startY: Float): Float {
        var y = startY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText("Academy Manager", MARGIN, y, paint)
        y += 30f
        paint.textSize = 18f
        canvas.drawText(subtitle, MARGIN, y, paint)
        y += 35f
        return y
    }

    private fun monthToDatePrefix(month: String): String {
        return try {
            val date = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(month)
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            ""
        }
    }
}
