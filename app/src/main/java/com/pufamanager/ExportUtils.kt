package com.pufamanager

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.pufamanager.data.entity.Attendance
import com.pufamanager.data.entity.Batch
import com.pufamanager.data.entity.Player
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtils {

    fun generateAttendanceZip(
        context: Context,
        batch: Batch,
        month: String,
        players: List<Player>,
        attendanceList: List<Attendance>
    ): File? {
        val batchPlayers = players.filter { it.batchId == batch.id }
        val monthAttendance = attendanceList.filter { it.date.startsWith(monthToDatePrefix(month)) }
        val dates = monthAttendance.map { it.date }.distinct().sorted()

        if (dates.isEmpty()) return null

        val pdfFiles = mutableListOf<File>()
        dates.forEach { date ->
            val dateAttendance = monthAttendance.filter { it.date == date }
            val presentPlayers = batchPlayers.filter { p -> dateAttendance.find { it.playerId == p.id }?.isPresent == true }.sortedBy { it.name }
            val absentPlayers = batchPlayers.filter { p -> dateAttendance.find { it.playerId == p.id }?.isPresent == false }.sortedBy { it.name }

            val pdfFile = createAttendancePdf(context, batch, date, presentPlayers, absentPlayers)
            pdfFiles.add(pdfFile)
        }

        val zipFile = File(context.cacheDir, "${batch.name.replace(" ", "_")}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            pdfFiles.forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                file.delete() // Clean up individual PDFs
            }
        }
        return zipFile
    }

    private fun createAttendancePdf(
        context: Context,
        batch: Batch,
        date: String,
        present: List<Player>,
        absent: List<Player>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = 50f

        // Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("PUFA Manager Hub", 50f, y, paint)
        y += 25f
        paint.textSize = 16f
        canvas.drawText("Attendance Report", 50f, y, paint)
        y += 35f

        paint.textSize = 14f
        canvas.drawText("Batch: ${batch.name}", 50f, y, paint)
        y += 20f
        canvas.drawText("Date: $date", 50f, y, paint)
        y += 40f

        // Present Section
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Present Players", 50f, y, paint)
        y += 25f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        if (present.isEmpty()) {
            canvas.drawText("None", 70f, y, paint)
            y += 20f
        } else {
            present.forEach { player ->
                if (y > 780) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                }
                canvas.drawText("• ${player.name}", 70f, y, paint)
                y += 20f
            }
        }

        y += 20f

        // Absent Section
        if (y > 750) {
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 50f
        }
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("Absent Players", 50f, y, paint)
        y += 25f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        if (absent.isEmpty()) {
            canvas.drawText("None", 70f, y, paint)
            y += 20f
        } else {
            absent.forEach { player ->
                if (y > 780) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                }
                canvas.drawText("• ${player.name}", 70f, y, paint)
                y += 20f
            }
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "$date.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    private fun monthToDatePrefix(month: String): String {
        return try {
            val date = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).parse(month)
            java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            ""
        }
    }
}
