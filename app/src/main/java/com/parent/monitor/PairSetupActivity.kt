package com.parent.monitor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class PairSetupActivity : AppCompatActivity() {

    companion object {
        private const val RELAY_URL = "wss://relay-server-production-bf46.up.railway.app/api/ws"
        private const val RELAY_HTTP = "https://relay-server-production-bf46.up.railway.app"
        private const val POLL_INTERVAL_MS = 4000L
    }

    private lateinit var ivQrCode: ImageView
    private lateinit var tvPairCode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnGenerate: Button
    private lateinit var btnShare: Button
    private lateinit var progressBar: ProgressBar

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()
    private var currentCode: String = ""
    private var pollRunnable: Runnable? = null
    private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pair_setup)
        window.statusBarColor = 0xFF060612.toInt()
        window.navigationBarColor = 0xFF060612.toInt()

        ivQrCode    = findViewById(R.id.ivQrCode)
        tvPairCode  = findViewById(R.id.tvPairCode)
        tvStatus    = findViewById(R.id.tvStatus)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnShare    = findViewById(R.id.btnShare)
        progressBar = findViewById(R.id.pairProgress)

        btnGenerate.setOnClickListener { generateAndSavePairCode() }
        btnShare.setOnClickListener { shareCode() }

        // Load existing code from Firebase (e.g. re-opened activity)
        loadExistingCode()
    }

    // ── Load existing code from Firebase ──────────────────────────────────
    private fun loadExistingCode() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.database.reference
            .child("users").child(uid).child("pairCode")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val code = snap.getValue(String::class.java)
                    if (!code.isNullOrEmpty()) {
                        currentCode = code
                        applyCode(code)
                        startPolling(code)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ── Generate + save to Firebase ───────────────────────────────────────
    private fun generateAndSavePairCode() {
        stopPolling()
        connected = false
        val code = (100000..999999).random().toString()
        currentCode = code

        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.database.reference
            .child("users").child(uid).child("pairCode")
            .setValue(code)
            .addOnSuccessListener {
                applyCode(code)
                startPolling(code)
            }
            .addOnFailureListener {
                setStatus("Firebase error: ${it.message}", error = true)
            }
    }

    // ── Render QR + code text ─────────────────────────────────────────────
    private fun applyCode(code: String) {
        tvPairCode.text = code
        btnShare.visibility = View.VISIBLE
        setStatus("Waiting for child to connect...", error = false)

        val payload = "$RELAY_URL|$code"
        val bmp = generateQr(payload, 600)
        ivQrCode.setImageBitmap(bmp)
        ivQrCode.visibility = View.VISIBLE
    }

    private fun generateQr(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (bits[x, y]) Color.parseColor("#00E5FF") else Color.parseColor("#060612"))
        return bmp
    }

    // ── Poll server for child connection ──────────────────────────────────
    private fun startPolling(code: String) {
        stopPolling()
        pollRunnable = object : Runnable {
            override fun run() {
                if (connected) return
                val req = Request.Builder()
                    .url("$RELAY_HTTP/api/online/$code")
                    .build()
                httpClient.newCall(req).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        mainHandler.postDelayed(pollRunnable!!, POLL_INTERVAL_MS)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: ""
                        try {
                            val online = JSONObject(body).optBoolean("online", false)
                            mainHandler.post {
                                if (online && !connected) {
                                    connected = true
                                    onChildConnected()
                                } else if (!connected) {
                                    mainHandler.postDelayed(pollRunnable!!, POLL_INTERVAL_MS)
                                }
                            }
                        } catch (_: Exception) {
                            mainHandler.postDelayed(pollRunnable!!, POLL_INTERVAL_MS)
                        }
                    }
                })
            }
        }
        mainHandler.post(pollRunnable!!)
    }

    private fun stopPolling() {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    // ── Child connected ───────────────────────────────────────────────────
    private fun onChildConnected() {
        stopPolling()
        setStatus("✅ Child Connected!", error = false)
        tvStatus.setTextColor(0xFF00C853.toInt())
        btnGenerate.isEnabled = false

        // Navigate to MainActivity after short delay
        mainHandler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1800)
    }

    // ── Share code text ───────────────────────────────────────────────────
    private fun shareCode() {
        if (currentCode.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT,
                "GuardianEye pair code: $currentCode\nURL: $RELAY_URL")
        }
        startActivity(Intent.createChooser(intent, "Share Pair Code"))
    }

    private fun setStatus(msg: String, error: Boolean) {
        tvStatus.text = msg
        tvStatus.setTextColor(if (error) 0xFFFF4444.toInt() else 0xFF888899.toInt())
        tvStatus.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        httpClient.dispatcher.executorService.shutdown()
    }

    override fun onBackPressed() {
        // Prevent accidental back on first-time setup
    }
}
