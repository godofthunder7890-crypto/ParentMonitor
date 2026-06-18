package com.parent.monitor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(
            R.layout.fragment_settings, container, false)

        val activity = requireActivity() as MainActivity
        val prefs = activity.getSharedPreferences("config", Context.MODE_PRIVATE)
        val etUrl = view.findViewById<EditText>(R.id.etServerUrl)

        etUrl.setText(prefs.getString("server_url",
            "wss://optimal-inputs-beginners-opt.trycloudflare.com"))

        view.findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            val newUrl = etUrl.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                prefs.edit().putString("server_url", newUrl).apply()
                activity.wsManager?.updateUrl(newUrl)
                updateInfo("Reconnecting to: $newUrl")
            }
        }

        return view
    }

    fun updateInfo(info: String) {
        view?.findViewById<TextView>(R.id.tvConnectionInfo)?.text = info
    }
}
