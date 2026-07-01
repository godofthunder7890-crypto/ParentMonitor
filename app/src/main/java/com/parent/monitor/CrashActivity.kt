package com.parent.monitor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Friendly crash screen — replaces the "App has stopped" system dialog.
 */
class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ERROR = "error"
        const val EXTRA_STACK = "stacktrace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val tvMsg      = findViewById<TextView>(R.id.tvCrashMsg)
        val tvStack    = findViewById<TextView>(R.id.tvCrashStack)
        val btnRestart = findViewById<Button>(R.id.btnCrashRestart)

        tvMsg.text   = intent.getStringExtra(EXTRA_ERROR) ?: "An unexpected error occurred."
        tvStack.text = intent.getStringExtra(EXTRA_STACK) ?: ""

        btnRestart.setOnClickListener {
            val restart = Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(restart)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back — user must press Restart
    }
}
