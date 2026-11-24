package com.yuka.rrquiz

import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentUrl: String = UrlPrefs.DEFAULT_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        // Debugging WebView aktif supaya DOM halaman bisa di-inspect dari jauh
        // (Chrome DevTools via ADB). Matikan lagi sebelum rilis final.
        WebView.setWebContentsDebuggingEnabled(true)
        setupWebView()

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back HP: mundur di riwayat WebView dulu, baru keluar app.
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
