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
            "wss://optimal-inputs-beginners-opt.trycloudflare.com")!!

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

        // Manual save button
        view.findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            val newUrl = etUrl.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                applyNewUrl(newUrl)
            }
        }

        return view
    }

    private fun applyNewUrl(newUrl: String) {
        val activity = requireActivity() as MainActivity
        val prefs = activity.getSharedPreferences("config", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", newUrl).apply()
        activity.wsManager?.updateUrl(newUrl)
        view?.findViewById<EditText>(R.id.etServerUrl)?.setText(newUrl)
        generateQrCode(view, newUrl)
        updateInfo("Reconnecting to: $newUrl")
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
