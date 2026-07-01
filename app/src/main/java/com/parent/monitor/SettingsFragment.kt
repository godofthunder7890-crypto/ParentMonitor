package com.parent.monitor

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SettingsFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var latestApkUrl = ""
    private var latestTag    = ""

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        result.contents?.let { scanned ->
            val parts = scanned.split("|")
            val url = parts[0].trim()
            if (url.startsWith("ws://") || url.startsWith("wss://")) {
                val code = if (parts.size > 1) parts[1].trim() else ""
                applyNewUrl(url, code)
            } else {
                setInfo("Invalid QR — must be wss:// URL")
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? =
        i.inflate(R.layout.fragment_settings, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val act   = requireActivity() as MainActivity
        val prefs = act.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

        val etUrl      = view.findViewById<EditText>(R.id.etServerUrl)
        val etCode     = view.findViewById<EditText>(R.id.etPairCode)
        val etRepo     = view.findViewById<EditText>(R.id.etGithubRepo)
        val etToken    = view.findViewById<EditText>(R.id.etGithubToken)
        val btnSave    = view.findViewById<Button>(R.id.btnSaveUrl)
        val btnScan    = view.findViewById<Button>(R.id.btnScanQr)
        val btnPush    = view.findViewById<Button>(R.id.btnPushToChild)
        val btnCheck   = view.findViewById<Button>(R.id.btnCheckUpdate)
        val btnPushUpd = view.findViewById<Button>(R.id.btnPushUpdate)
        val btnSaveToken = view.findViewById<Button>(R.id.btnSaveGithubToken)
        val tvStatus   = view.findViewById<TextView>(R.id.tvConnectionInfo)
        val tvUpdStat  = view.findViewById<TextView>(R.id.tvUpdateStatus)
        val tvOtaVer   = view.findViewById<TextView>(R.id.tvOtaVersion)

        val curUrl   = prefs.getString(MainActivity.KEY_SERVER_URL, MainActivity.DEFAULT_URL) ?: MainActivity.DEFAULT_URL
        val curCode  = prefs.getString(MainActivity.KEY_PAIR_CODE, "") ?: ""
        val curRepo  = prefs.getString(MainActivity.KEY_GITHUB_REPO, "godofthunder7890-crypto/ChildMonitor") ?: ""
        // BUG #8 FIX: Use EncryptedSharedPreferences to protect GitHub PAT from rooted device reads
        val masterKey = androidx.security.crypto.MasterKey.Builder(act)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
        val ghPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            act, "github_prefs_enc", masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val curToken = ghPrefs.getString("github_token", "") ?: ""

        etUrl.setText(curUrl)
        etCode.setText(curCode)
        etRepo.setText(curRepo)
        if (curToken.isNotEmpty()) etToken.setText(curToken)
        generateQr(view, "$curUrl|$curCode")

        // BUG FIX: Token save karne ka koi UI nahi tha.
        // Ab user token field mein type kare aur SAVE button dabaye.
        btnSaveToken.setOnClickListener {
            val tok = etToken.text.toString().trim()
            ghPrefs.edit().putString("github_token", tok).apply()
            setInfo(if (tok.isNotEmpty()) "✅ GitHub token saved!" else "🗑 GitHub token cleared")
        }

        btnSave.setOnClickListener {
            val u = etUrl.text.toString().trim()
            val c = etCode.text.toString().trim()
            if (u.isNotEmpty()) applyNewUrl(u, c)
        }

        btnScan.setOnClickListener {
            val opts = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Server URL wala QR scan karo")
                setCameraId(0); setBeepEnabled(false); setBarcodeImageEnabled(false)
            }
            if (requireActivity().checkSelfPermission(android.Manifest.permission.CAMERA) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 202)
                return@setOnClickListener
            }
            qrScanLauncher.launch(opts)
        }

        btnPush.setOnClickListener {
            val u = etUrl.text.toString().trim()
            val c = etCode.text.toString().trim()
            if (u.isNotEmpty()) { pushUrlToChild(u, c); setInfo("📡 URL pushed to child!") }
        }

        // ─── OTA: Check GitHub releases ────────────────────────────────────
        btnCheck.setOnClickListener {
            val repo = etRepo.text.toString().trim()
            if (repo.isEmpty()) { tvUpdStat?.text = "Enter GitHub repo first (e.g. owner/ChildMonitor)"; return@setOnClickListener }
            prefs.edit().putString(MainActivity.KEY_GITHUB_REPO, repo).apply()
            tvUpdStat?.text = "⏳ Checking latest release..."
            tvUpdStat?.setTextColor(0xFF888899.toInt())
            btnPushUpd.isEnabled = false
            // BUG #7 FIX: Capture token on main thread before launching background Thread
            val token = ghPrefs.getString("github_token", "") ?: ""

            Thread {
                try {
                    val conn = URL("https://api.github.com/repos/$repo/releases/latest")
                        .openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    // BUG FIX: Private repos ke liye auth header zaroori hai
                    // Bina is header ke private repo 404 deta tha — OTA kabhi kaam nahi karta tha
                    // BUG #7 FIX: token captured before Thread (see below)
                    if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "token $token")
                    conn.connectTimeout = 10000; conn.readTimeout = 10000
                    // FIX: conn.inputStream throws FileNotFoundException on non-2xx responses.
                    // Check responseCode first; use errorStream for 4xx/5xx to get error body.
                    val code = conn.responseCode
                    val body = if (code in 200..299)
                        conn.inputStream.bufferedReader().readText()
                    else {
                        val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                        conn.disconnect()
                        throw Exception(if (code == 401 || code == 403) "Auth failed — check GitHub token" else "HTTP $code: $err")
                    }
                    conn.disconnect()
                    val json = JSONObject(body)

                    val tag    = json.optString("tag_name", "unknown")
                    val assets = json.optJSONArray("assets")
                    val apk    = (0 until (assets?.length() ?: 0))
                        .map { assets!!.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk") }

                    latestTag    = tag
                    latestApkUrl = apk?.optString("browser_download_url") ?: ""
                    val apkName  = apk?.optString("name") ?: "no APK found"

                    handler.post {
                        tvOtaVer?.text = tag
                        if (latestApkUrl.isNotEmpty()) {
                            tvUpdStat?.text = "✅ Latest: $tag\n📦 $apkName\nPress PUSH APK to install on child"
                            tvUpdStat?.setTextColor(0xFF00C853.toInt())
                            btnPushUpd.isEnabled = true
                        } else {
                            tvUpdStat?.text = "⚠️ Release $tag found but no .apk asset\nUpload APK to GitHub release"
                            tvUpdStat?.setTextColor(0xFFFFD600.toInt())
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        tvUpdStat?.text = "❌ Error: ${e.message}"
                        tvUpdStat?.setTextColor(0xFFFF1744.toInt())
                    }
                }
            }.start()
        }

        // ─── OTA: Push APK download URL to child ───────────────────────────
        btnPushUpd.setOnClickListener {
            if (latestApkUrl.isEmpty()) return@setOnClickListener
            try {
                act.sendCommandObj(JSONObject().apply {
                    put("command", "update_from_url")
                    put("url", latestApkUrl)
                    put("version", latestTag)
                })
                tvUpdStat?.text = "🚀 Update $latestTag pushed!\nChild is downloading and installing APK..."
                tvUpdStat?.setTextColor(0xFF00E5FF.toInt())
                btnPushUpd.isEnabled = false
            } catch (_: Exception) {}
        }
    }

    private fun applyNewUrl(newUrl: String, newCode: String) {
        val act   = requireActivity() as MainActivity
        val prefs = act.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(MainActivity.KEY_SERVER_URL, newUrl)
            .putString(MainActivity.KEY_PAIR_CODE,  newCode)
            .apply()
        act.reconnect(newUrl, newCode)
        pushUrlToChild(newUrl, newCode)
        view?.findViewById<EditText>(R.id.etServerUrl)?.setText(newUrl)
        view?.findViewById<EditText>(R.id.etPairCode)?.setText(newCode)
        generateQr(view, "$newUrl|$newCode")
        setInfo("✅ Saved & reconnected!")
    }

    private fun pushUrlToChild(url: String, code: String) {
        try {
            (activity as? MainActivity)?.sendCommandObj(JSONObject().apply {
                put("command",   "update_url")
                put("url",       url)
                put("pair_code", code)
            })
        } catch (_: Exception) {}
    }

    private fun generateQr(view: View?, content: String) {
        try {
            val bmp: Bitmap = BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 400, 400)
            view?.findViewById<ImageView>(R.id.ivQrCode)?.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun setInfo(msg: String) {
        view?.findViewById<TextView>(R.id.tvConnectionInfo)?.text = msg
    }
}
