package com.parent.monitor

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnCreate: Button
    private lateinit var btnForgot: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth

        // If already logged in → skip to MainActivity
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        window.statusBarColor = 0xFF060612.toInt()
        window.navigationBarColor = 0xFF060612.toInt()

        etEmail    = findViewById(R.id.etLoginEmail)
        etPassword = findViewById(R.id.etLoginPassword)
        btnLogin   = findViewById(R.id.btnLogin)
        btnCreate  = findViewById(R.id.btnCreateAccount)
        btnForgot  = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.loginProgress)
        tvStatus   = findViewById(R.id.tvLoginStatus)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                showStatus("Email aur password dono zaruri hain", error = true); return@setOnClickListener
            }
            setLoading(true)
            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener { e -> setLoading(false); showStatus(e.message ?: "Login failed", error = true) }
        }

        btnCreate.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                showStatus("Email aur password dono zaruri hain", error = true); return@setOnClickListener
            }
            if (pass.length < 6) {
                showStatus("Password minimum 6 characters ka hona chahiye", error = true); return@setOnClickListener
            }
            setLoading(true)
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener { e -> setLoading(false); showStatus(e.message ?: "Account create failed", error = true) }
        }

        btnForgot.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                showStatus("Pehle email field mein apna email likho", error = true); return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener { showStatus("Password reset email bheja gaya!", error = false) }
                .addOnFailureListener { e -> showStatus(e.message ?: "Reset failed", error = true) }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled  = !loading
        btnCreate.isEnabled = !loading
    }

    private fun showStatus(msg: String, error: Boolean) {
        tvStatus.text = msg
        tvStatus.setTextColor(if (error) 0xFFFF4444.toInt() else 0xFF00C853.toInt())
        tvStatus.visibility = View.VISIBLE
    }
}
