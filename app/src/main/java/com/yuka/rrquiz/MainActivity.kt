package com.yuka.rrquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnPlay: ImageButton
    private lateinit var tvAutoScore: TextView
    private lateinit var progressAuto: ProgressBar
    private lateinit var dbHelper: BankSoalDbHelper
    private var currentUrl: String = UrlPrefs.DEFAULT_URL
    private var autoRunner: AutoAnswerRunner? = null
    private var isAutoRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        // Debugging WebView aktif supaya DOM halaman bisa di-inspect dari jauh
        // (Chrome DevTools via ADB). Matikan lagi sebelum rilis final.
        WebView.setWebContentsDebuggingEnabled(true)
        setupWebView()

        dbHelper = BankSoalDbHelper(this)

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // --- Tombol Play: demo pengerjaan otomatis ---
        btnPlay = findViewById(R.id.btnPlay)
        tvAutoScore = findViewById(R.id.tvAutoScore)
        progressAuto = findViewById(R.id.progressAuto)
        val lastScore = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_LAST_AUTO_SCORE, -1)
        if (lastScore >= 0) {
            tvAutoScore.text = getString(R.string.auto_score_format, lastScore)
        }
        btnPlay.setOnClickListener {
            if (!isAutoRunning) showAutoConfirmDialog()
        }

        loadSavedUrl()
    }

    override fun onResume() {
        super.onResume()
        // Kalau URL diubah di Settings, muat ulang saat kembali ke sini.
        val savedUrl = UrlPrefs.getUrl(this)
        if (savedUrl != currentUrl) {
            loadSavedUrl()
        }
    }

    override fun onDestroy() {
        autoRunner?.cancel()
        dbHelper.close()
        super.onDestroy()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        // Tetap di dalam app, jangan lempar ke browser luar.
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun loadSavedUrl() {
        currentUrl = UrlPrefs.getUrl(this)
        webView.loadUrl(currentUrl)
    }

    private fun showAutoConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_title)
            .setMessage(R.string.auto_message)
            .setPositiveButton(R.string.auto_run) { _, _ -> startAutoRun() }
            .setNegativeButton(R.string.auto_cancel, null)
            .show()
    }

    private fun startAutoRun() {
        if (dbHelper.count() == 0) {
            Toast.makeText(this, R.string.auto_bank_empty, Toast.LENGTH_LONG).show()
            return
        }
        isAutoRunning = true
        btnPlay.visibility = View.GONE
        progressAuto.visibility = View.VISIBLE
        tvAutoScore.text = ""
        autoRunner = AutoAnswerRunner(
            this,
            webView,
            dbHelper,
            onFinished = { result -> onAutoFinished(result) },
            onNotQuizPage = { onAutoNotQuiz() }
        )
        autoRunner?.start()
    }

    private fun onAutoFinished(result: AutoResult) {
        isAutoRunning = false
        autoRunner = null
        btnPlay.visibility = View.VISIBLE
        progressAuto.visibility = View.GONE
        tvAutoScore.text = getString(R.string.auto_score_format, result.percent)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_AUTO_SCORE, result.percent)
            .apply()
        val msg = StringBuilder()
        msg.append(
            getString(
                R.string.auto_result_format,
                result.answered,
                result.total,
                result.percent
            )
        )
        if (result.unmatched.isEmpty()) {
            msg.append("\n").append(getString(R.string.auto_result_all_matched))
        } else {
            msg.append("\n").append(
                getString(R.string.auto_result_unmatched, result.unmatched.joinToString(", "))
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_result_title)
            .setMessage(msg.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun onAutoNotQuiz() {
        isAutoRunning = false
        autoRunner = null
        btnPlay.visibility = View.VISIBLE
        progressAuto.visibility = View.GONE
        Toast.makeText(this, R.string.auto_not_quiz, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back HP: mundur di riwayat WebView dulu, baru keluar app.
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val PREFS_NAME = "rrquiz_prefs"
        private const val KEY_LAST_AUTO_SCORE = "last_auto_score"
    }
}
