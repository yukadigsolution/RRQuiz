package com.yuka.rrquiz

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var tvBankStatus: TextView
    private lateinit var tableBankPreview: TableLayout
    private lateinit var dbHelper: BankSoalDbHelper

    private val pickExcelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_file), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        // Parse di thread latar supaya UI tidak beku saat file besar.
        Thread {
            val result = ExcelImporter.importFiles(this, uris)
            val saved = dbHelper.insertAll(result.rows)
            runOnUiThread {
                refreshBankSection(result.success, result.failedRows, result.failedFiles)
                Toast.makeText(
                    this,
                    getString(R.string.imported_toast, saved),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        dbHelper = BankSoalDbHelper(this)

        urlInput = findViewById(R.id.inputUrl)
        urlInput.setText(UrlPrefs.getUrl(this))
        // Kursor di akhir teks biar langsung bisa edit ujung URL.
        urlInput.setSelection(urlInput.text.length)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val savedUrl = UrlPrefs.saveUrl(this, urlInput.text.toString())
            Toast.makeText(this, "URL disimpan: $savedUrl", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            urlInput.setText(UrlPrefs.DEFAULT_URL)
            urlInput.setSelection(urlInput.text.length)
            Toast.makeText(this, "Dikembalikan ke URL default", Toast.LENGTH_SHORT).show()
        }

        // --- Bank Soal: upload Excel ---
        tvBankStatus = findViewById(R.id.tvBankStatus)
        tableBankPreview = findViewById(R.id.tableBankPreview)

        findViewById<Button>(R.id.btnPickExcel).setOnClickListener {
            pickExcelLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/comma-separated-values"
                )
            )
        }

        findViewById<Button>(R.id.btnClearBank).setOnClickListener {
            dbHelper.clearAll()
            refreshBankSection()
            Toast.makeText(this, getString(R.string.bank_cleared), Toast.LENGTH_SHORT).show()
        }

        refreshBankSection()
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }

    /** Muat ulang status dan preview dari isi SQLite saat ini. */
    private fun refreshBankSection(success: Int? = null, failedRows: Int = 0, failedFiles: Int = 0) {
        val rows = dbHelper.getAll()
        tvBankStatus.text = if (success != null) {
            val extra = if (failedFiles > 0) " • File gagal dibaca: $failedFiles" else ""
            getString(R.string.bank_status, success, failedRows) + extra +
                "\n" + getString(R.string.bank_count, rows.size)
        } else if (rows.isEmpty()) {
            getString(R.string.bank_empty)
        } else {
            getString(R.string.bank_count, rows.size)
        }

        tableBankPreview.removeAllViews()
        if (rows.isNotEmpty()) {
            tableBankPreview.addView(makePreviewRow("No", "Soal", "Jawaban", bold = true))
            val shown = rows.take(MAX_PREVIEW_ROWS)
            for (row in shown) {
                tableBankPreview.addView(
                    makePreviewRow(row.id.toString(), row.soal, row.jawaban, bold = false)
                )
            }
            if (rows.size > MAX_PREVIEW_ROWS) {
                val more = TextView(this).apply {
                    text = getString(R.string.preview_more, rows.size - MAX_PREVIEW_ROWS)
                    setPadding(8, 8, 8, 8)
                }
                tableBankPreview.addView(more)
            }
        }
    }

    private fun makePreviewRow(no: String, soal: String, jawaban: String, bold: Boolean): TableRow {
        val row = TableRow(this)
        val cells = listOf(no, soal, jawaban)
        for ((index, text) in cells.withIndex()) {
            val tv = TextView(this).apply {
                this.text = text
                setPadding(8, 8, 8, 8)
                if (index > 0) {
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            if (index == 0) tv.gravity = Gravity.TOP
            row.addView(tv)
        }
        return row
    }

    companion object {
        private const val MAX_PREVIEW_ROWS = 50
    }
}
