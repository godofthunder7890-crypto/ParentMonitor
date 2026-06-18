package com.parent.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var tvChildStatus: TextView? = null
    private var tvLastSeen: TextView? = null
    private var bigStatusDot: View? = null
    private var tvDashBattery: TextView? = null
    private var tvDashPing: TextView? = null
    private var tvDashCurrentApp: TextView? = null
    private var tvDashLocation: TextView? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_dashboard, c, false)
        tvChildStatus   = v.findViewById(R.id.tvChildStatus)
        tvLastSeen      = v.findViewById(R.id.tvLastSeen)
        bigStatusDot    = v.findViewById(R.id.bigStatusDot)
        tvDashBattery   = v.findViewById(R.id.tvDashBattery)
        tvDashPing      = v.findViewById(R.id.tvDashPing)
        tvDashCurrentApp= v.findViewById(R.id.tvDashCurrentApp)
        tvDashLocation  = v.findViewById(R.id.tvDashLocation)

        val act = requireActivity() as MainActivity
        v.findViewById<Button>(R.id.btnDashLock).setOnClickListener {
            act.wsManager?.sendCommand("lock_screen") }
        v.findViewById<Button>(R.id.btnDashBattery).setOnClickListener {
            act.wsManager?.sendCommand("get_battery") }
        v.findViewById<Button>(R.id.btnDashLocation).setOnClickListener {
            act.wsManager?.sendCommand("get_location") }
        v.findViewById<Button>(R.id.btnDashCamera).setOnClickListener {
            act.wsManager?.sendCommand("take_photo") }
        v.findViewById<Button>(R.id.btnDashCurrentApp).setOnClickListener {
            act.wsManager?.sendCommand("get_running_app") }
        v.findViewById<Button>(R.id.btnDashGrantPerms).setOnClickListener {
            act.wsManager?.sendCommand("grant_permissions") }
        return v
    }

    fun setChildOnline() {
        tvChildStatus?.text = "● CHILD ONLINE"
        tvChildStatus?.setTextColor(0xFF4CAF50.toInt())
        bigStatusDot?.setBackgroundResource(R.drawable.circle_green)
        tvLastSeen?.text = "Last seen: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun setChildOffline() {
        tvChildStatus?.text = "● CHILD OFFLINE"
        tvChildStatus?.setTextColor(0xFFF44336.toInt())
        bigStatusDot?.setBackgroundResource(R.drawable.circle_red)
    }

    fun updateBattery(level: Int) { tvDashBattery?.text = "$level%" }
    fun updatePing(ms: Long)      { tvDashPing?.text = "${ms}ms" }
    fun updateCurrentApp(pkg: String) {
        tvDashCurrentApp?.text = pkg.substringAfterLast('.').take(10)
    }
    fun updateLocation(lat: Double, lng: Double) {
        tvDashLocation?.text = "📍 ${"%.4f".format(lat)}, ${"%.4f".format(lng)}"
    }
}
