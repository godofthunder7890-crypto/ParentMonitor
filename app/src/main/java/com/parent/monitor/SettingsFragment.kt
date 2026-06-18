package com.parent.monitor

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
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

class SettingsFragment : Fragment() {

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        result.contents?.let { scannedUrl ->
            if (scannedUrl.startsWith("ws://") || scannedUrl.startsWith("wss://")) {
                applyNewUrl(scannedUrl)
            } else {
                updateInfo("Invalid QR — must be a wss:// URL")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val activity = requireActivity() as MainActivity
        val prefs = activity.getSharedPreferences("config", Context.MODE_PRIVATE)
        val etUrl = view.findViewById<EditText>(R.id.etServerUrl)
        val currentUrl = prefs.getString("server_url",
            "wss://dbb8b339-6f63-4353-b557-828369c2aaf6-00-1ox04gta0r1v2.sisko.replit.dev/api/ws")!!

        etUrl.setText(currentUrl)
        generateQrCode(view, currentUrl)

        // Scan QR button
        view.findViewById<Button>(R.id.btnScanQr).setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Server URL wala QR scan karo")
                setCameraId(0)
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
            }
            qrScanLauncher.launch(options)
        }

        // Save + auto push to child
        view.findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            val newUrl = etUrl.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                applyNewUrl(newUrl)
            }
        }

        // Manual push to child button
        view.findViewById<Button>(R.id.btnPushToChild).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                pushUrlToChild(url)
                updateInfo("📡 URL pushed to child!")
            }
        }

        return view
    }

    private fun applyNewUrl(newUrl: String) {
        val activity = requireActivity() as MainActivity
        val prefs = activity.getSharedPreferences("config", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", newUrl).apply()
        activity.wsManager?.updateUrl(newUrl)
        // Auto push new URL to child device
        pushUrlToChild(newUrl)
        view?.findViewById<EditText>(R.id.etServerUrl)?.setText(newUrl)
        generateQrCode(view, newUrl)
        updateInfo("✅ Saved & pushed to child!")
    }

    private fun pushUrlToChild(url: String) {
        try {
            val act = requireActivity() as MainActivity
            act.wsManager?.sendCommandObj(JSONObject().apply {
                put("command", "update_url")
                put("url", url)
            })
        } catch (_: Exception) {}
    }

    private fun generateQrCode(view: View?, url: String) {
        try {
            val encoder = BarcodeEncoder()
            val bitmap: Bitmap = encoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 400, 400)
            view?.findViewById<ImageView>(R.id.ivQrCode)?.setImageBitmap(bitmap)
        } catch (e: Exception) { }
    }

    fun updateInfo(info: String) {
        view?.findViewById<TextView>(R.id.tvConnectionInfo)?.text = info
    }
}
