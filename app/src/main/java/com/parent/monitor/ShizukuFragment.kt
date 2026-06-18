package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

class ShizukuFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val root = LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        root.addView(TextView(ctx).apply { text="SHIZUKU CONTROLS"; setTextColor(0xFF00E5FF.toInt()); textSize=16f; typeface=android.graphics.Typeface.MONOSPACE })
        root.addView(TextView(ctx).apply { text="Shizuku-powered privileged commands"; setTextColor(0xFF666688.toInt()); textSize=12f; setPadding(0,8,0,24) })
        val btns = listOf(
            "GRANT ALL PERMISSIONS" to "grant_permissions",
            "DISABLE CHILD WIFI" to "wifi_off",
            "ENABLE CHILD WIFI" to "wifi_on",
            "REBOOT CHILD DEVICE" to "reboot",
            "CLEAR APP DATA" to "clear_app_data"
        )
        btns.forEach { (label, cmd) ->
            root.addView(Button(ctx).apply {
                text = label; textSize=12f
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF0D0D21.toInt())
                setTextColor(0xFFAAAAAA.toInt())
                layoutParams = LinearLayout.LayoutParams(-1,-2).also { it.bottomMargin=8 }
                setOnClickListener { act.wsManager.sendCommand(cmd) }
            })
        }
        return root
    }
}