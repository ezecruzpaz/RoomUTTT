package com.example.roomuttt.ui.terms

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.roomuttt.R

class TermsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        // Botón de regreso (flecha)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            finish() // Regresa a RegisterActivity
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