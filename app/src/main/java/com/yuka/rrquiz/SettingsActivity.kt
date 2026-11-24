package com.yuka.rrquiz

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
    }
}
