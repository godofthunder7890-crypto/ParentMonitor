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
        result.contents?.let { scanned ->
            val parts = scanned.split("|")
            val url = parts[0].trim()
            if (url.startsWith("ws://") || url.startsWith("wss://")) {
                val code = if (parts.size > 1) parts[1].trim() else ""
                applyNewUrl(url, code)
            } else {
                updateInfo("Invalid QR — must be a wss:// URL")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val activity = requireActivity() as MainActivity
        val prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

        val etUrl      = view.findViewById<EditText>(R.id.etServerUrl)
        val etPairCode = view.findViewById<EditText>(R.id.etPairCode)

        val currentUrl  = prefs.getString(MainActivity.KEY_SERVER_URL, MainActivity.DEFAULT_URL)!!
        val currentCode = prefs.getString(MainActivity.KEY_PAIR_CODE, "")!!

        etUrl.setText(currentUrl)
        etPairCode.setText(currentCode)
        // QR encodes "url|paircode" — child app scans this to get both at once
        generateQrCode(view, "$currentUrl|$currentCode")

        view.findViewById<Button>(R.id.btnScanQr).setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Server URL wala QR scan karo")
                setCameraId(0); setBeepEnabled(false); setBarcodeImageEnabled(false)
            }
            qrScanLauncher.launch(options)
        }

        view.findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            val newUrl  = etUrl.text.toString().trim()
            val newCode = etPairCode.text.toString().trim()
            if (newUrl.isNotEmpty()) applyNewUrl(newUrl, newCode)
        }

        view.findViewById<Button>(R.id.btnPushToChild).setOnClickListener {
            val url  = etUrl.text.toString().trim()
            val code = etPairCode.text.toString().trim()
            if (url.isNotEmpty()) {
                pushUrlToChild(url, code)
                updateInfo("📡 URL + pair code pushed to child!")
            }
        }

        return view
    }

    private fun applyNewUrl(newUrl: String, newCode: String) {
        val activity = requireActivity() as MainActivity
        val prefs = activity.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(MainActivity.KEY_SERVER_URL, newUrl)
            .putString(MainActivity.KEY_PAIR_CODE, newCode)
            .apply()
        activity.wsManager?.updateUrl(newUrl, newCode)
        pushUrlToChild(newUrl, newCode)
        view?.findViewById<EditText>(R.id.etServerUrl)?.setText(newUrl)
        view?.findViewById<EditText>(R.id.etPairCode)?.setText(newCode)
        generateQrCode(view, "$newUrl|$newCode")
        updateInfo("✅ Saved & pushed to child!")
    }

    private fun pushUrlToChild(url: String, code: String) {
        try {
            val act = requireActivity() as MainActivity
            act.wsManager?.sendCommandObj(JSONObject().apply {
                put("command", "update_url")
                put("url", url)
                put("pair_code", code)
            })
        } catch (_: Exception) {}
    }

    private fun generateQrCode(view: View?, content: String) {
        try {
            val encoder = BarcodeEncoder()
            val bitmap: Bitmap = encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 400, 400)
            view?.findViewById<ImageView>(R.id.ivQrCode)?.setImageBitmap(bitmap)
        } catch (_: Exception) {}
    }

    fun updateInfo(info: String) {
        view?.findViewById<TextView>(R.id.tvConnectionInfo)?.text = info
    }
}
