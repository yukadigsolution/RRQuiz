package com.yuka.rrquiz

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Import bank soal dari file Excel/CSV.
 * Struktur: kolom pertama = soal, kolom kedua = jawaban.
 * - .xlsx/.xls diparse dengan Apache POI (sheet pertama).
 * - .csv diparse native di Kotlin (dukung koma/semicolon + field ber-kutip).
 * Baris header (kolom pertama "soal"/"pertanyaan", kolom kedua "jawaban")
 * dihapus, baris kosong / tanpa soal / tanpa jawaban dilewati dan dihitung gagal.
 */
data class ImportResult(
    val rows: List<Pair<String, String>>,
    val success: Int,
    val failedRows: Int,
    val failedFiles: Int
)

object ExcelImporter {

    private val HEADER_SOAL = setOf("soal", "pertanyaan", "question", "no", "nomor")
    private val HEADER_JAWABAN = setOf("jawaban", "jawab", "answer", "kunci")

    fun importFiles(context: Context, uris: List<Uri>): ImportResult {
        val rows = mutableListOf<Pair<String, String>>()
        var failedRows = 0
        var failedFiles = 0
        for (uri in uris) {
            try {
                val name = displayName(context, uri).lowercase()
                val raw = if (name.endsWith(".csv")) parseCsv(context, uri) else parseExcel(context, uri)
                val (clean, failed) = cleanRows(raw)
                rows += clean
                failedRows += failed
            } catch (e: Exception) {
                failedFiles++
            }
        }
        return ImportResult(rows, rows.size, failedRows, failedFiles)
    }

    /** Hapus header + baris kosong/tak lengkap. Kembalikan (baris bersih, jumlah baris gagal). */
    private fun cleanRows(raw: List<Pair<String, String>>): Pair<List<Pair<String, String>>, Int> {
        val clean = mutableListOf<Pair<String, String>>()
        var failed = 0
        for ((index, pair) in raw.withIndex()) {
            val soal = pair.first.trim()
            val jawaban = pair.second.trim()
            if (index == 0 && soal.lowercase() in HEADER_SOAL && jawaban.lowercase() in HEADER_JAWABAN) {
                continue // baris header
            }
            if (soal.isEmpty() || jawaban.isEmpty()) {
                failed++
                continue
            }
            clean.add(soal to jawaban)
        }
        return clean to failed
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: ""
                }
            }
        return uri.lastPathSegment ?: ""
    }

    private fun parseExcel(context: Context, uri: Uri): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val workbook = WorkbookFactory.create(input)
            try {
                val sheet = workbook.getSheetAt(0) ?: return rows
                val formatter = DataFormatter()
                for (row in sheet) {
                    val soal = row.getCell(0)?.let { formatter.formatCellValue(it) } ?: ""
                    val jawaban = row.getCell(1)?.let { formatter.formatCellValue(it) } ?: ""
                    rows.add(soal to jawaban)
                }
            } finally {
                workbook.close()
            }
        }
        return rows
    }

    private fun parseCsv(context: Context, uri: Uri): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val lines = reader.readLines().filter { it.isNotBlank() }
                if (lines.isEmpty()) return rows
                val delimiter = detectDelimiter(lines.first())
                for (line in lines) {
                    val cells = parseCsvLine(line, delimiter)
                    val soal = cells.getOrNull(0) ?: ""
                    val jawaban = cells.getOrNull(1) ?: ""
                    rows.add(soal to jawaban)
                }
            }
        }
        return rows
    }

    /** Pilih pemisah yang paling sering muncul di baris pertama (koma vs titik-koma). */
    private fun detectDelimiter(firstLine: String): Char {
        val commas = firstLine.count { it == ',' }
        val semicolons = firstLine.count { it == ';' }
        return if (semicolons > commas) ';' else ','
    }

    /** Satu baris CSV dengan dukungan field ber-kutip ("...") dan "" di dalam kutip. */
    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
