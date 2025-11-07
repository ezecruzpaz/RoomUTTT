package com.roomu.app.ui.terms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.roomu.app.R

class TermsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        // Botón de regreso (flecha)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            finish() // Regresa a RegisterActivity
        }

        val linkLeerMas: TextView? = findViewById(R.id.link_leer_mas)
        linkLeerMas?.setOnClickListener {
            val url = "https://sites.google.com/view/roomu/políticas-de-uso?authuser=0"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }


        // Botón de aceptar políticas
        val btnAccept = findViewById<Button>(R.id.btn_accept)
        btnAccept.setOnClickListener {
            // Guarda que el usuario aceptó las políticas
            val sharedPreferences = getSharedPreferences("RoomUPrefs", MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("politicas_aceptadas", true).apply()

            // Regresa a RegisterActivity
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}