package com.blackcode.poscandykush

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val btnBack: Button = findViewById(R.id.btn_back)

        // Set up back button
        btnBack.setOnClickListener {
            finish() // Close admin activity and return to menu
        }

        // TODO: Implement admin dashboard features
        // - View sales reports
        // - Manage inventory
        // - User management
        // - Settings
        // etc.
    }
}

