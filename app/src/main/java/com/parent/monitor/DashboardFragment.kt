package com.parent.monitor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var tvLastSeen: TextView?    = null
    private var tvDashCurrentApp: TextView? = null
    private var tvDashBattery: TextView? = null
    private var tvDashPing: TextView?    = null
    private var tvDashLocation: TextView? = null
    private var tvActivityLog: TextView? = null
    private var bigStatusDot: View?  = null
    private var statusRing: View?    = null
    private var btnDashLock: Button? = null
    private var btnDashCamera: Button? = null
    private var btnDashLocation: Button? = null
    private var btnDashBattery: Button? = null
    private var btnDashCurrentApp: Button? = null
    private var btnDashGrantPerms: Button? = null
    private var btnDashEmergencyLock: Button? = null

    private val handler = Handler(Looper.getMainLooper())
    private var pingPulse: Runnable? = null
    private val activityLog = mutableListOf<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? =
        i.inflate(R.layout.fragment_dashboard, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvChildStatus    = view.findViewById(R.id.tvChildStatus)
        tvLastSeen       = view.findViewById(R.id.tvLastSeen)
        tvDashCurrentApp = view.findViewById(R.id.tvDashCurrentApp)
        tvDashBattery    = view.findViewById(R.id.tvDashBattery)
        tvDashPing       = view.findViewById(R.id.tvDashPing)
        tvDashLocation   = view.findViewById(R.id.tvDashLocation)
        tvActivityLog    = view.findViewById(R.id.tvActivityLog)
        bigStatusDot     = view.findViewById(R.id.bigStatusDot)
        statusRing       = view.findViewById(R.id.statusRing)
        btnDashLock      = view.findViewById(R.id.btnDashLock)
        btnDashCamera    = view.findViewById(R.id.btnDashCamera)
        btnDashLocation  = view.findViewById(R.id.btnDashLocation)
        btnDashBattery   = view.findViewById(R.id.btnDashBattery)
        btnDashCurrentApp= view.findViewById(R.id.btnDashCurrentApp)
        btnDashGrantPerms= view.findViewById(R.id.btnDashGrantPerms)
        btnDashEmergencyLock = view.findViewById(R.id.btnDashEmergencyLock)

        (activity as? MainActivity)?.dashboardFragment = this

        btnDashLock?.setOnClickListener          { sendCmd("lock_screen") }
        btnDashCamera?.setOnClickListener        { sendCmd("take_photo") }
        btnDashLocation?.setOnClickListener      { sendCmd("get_location") }
        btnDashBattery?.setOnClickListener       { sendCmd("get_battery") }
        btnDashCurrentApp?.setOnClickListener    { sendCmd("get_running_app") }
        btnDashGrantPerms?.setOnClickListener    { sendCmd("grant_permissions") }
        btnDashEmergencyLock?.setOnClickListener {
            sendCmd("emergency_lock")
            addLog("⚡ Emergency lock sent")
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.dashboardFragment = null
        stopPingPulse()
        super.onDestroyView()
    }

    fun updateOnline(online: Boolean) {
        if (!isAdded) return
        val color = if (online) 0xFF00C853.toInt() else 0xFFFF1744.toInt()
        tvChildStatus?.text = if (online) "ONLINE" else "OFFLINE"
        tvChildStatus?.setTextColor(color)
        bigStatusDot?.setBackgroundColor(color)
        statusRing?.setBackgroundColor(color)
        tvLastSeen?.text = if (online) "Connected ${timeFmt.format(Date())}" else "Last seen ${timeFmt.format(Date())}"
        if (online) { addLog("✅ Child device connected"); startPingPulse() }
        else        { addLog("❌ Child device disconnected"); stopPingPulse() }
    }

    fun updateBattery(pct: Int, charging: Boolean) {
        if (!isAdded) return
        val symbol = if (charging) "⚡" else "%"
        tvDashBattery?.text = "$pct$symbol"
        tvDashBattery?.setTextColor(when {
            pct > 50 -> 0xFF00C853.toInt()
            pct > 20 -> 0xFFFFD600.toInt()
            else     -> 0xFFFF1744.toInt()
        })
        addLog("🔋 Battery: $pct%${if (charging) " (charging)" else ""}")
    }

    fun updatePing(ms: Long) {
        if (!isAdded) return
        tvDashPing?.text = "${ms}ms"
        tvDashPing?.setTextColor(when {
            ms < 150 -> 0xFF00C853.toInt()
            ms < 400 -> 0xFFFFD600.toInt()
            else     -> 0xFFFF1744.toInt()
        })
    }

    fun updateLocation(lat: Double, lng: Double) {
        if (!isAdded) return
        tvDashLocation?.text = "%.4f\n%.4f".format(lat, lng)
        addLog("📍 Location: %.4f, %.4f".format(lat, lng))
    }

    fun updateCurrentApp(pkg: String) {
        if (!isAdded) return
        val name = pkg.substringAfterLast('.')
        tvDashCurrentApp?.text = name
        addLog("📱 App: $name")
    }

    fun addLog(msg: String) {
        if (!isAdded) return
        val line = "[${timeFmt.format(Date())}] $msg"
        activityLog.add(0, line)
        if (activityLog.size > 8) activityLog.removeAt(activityLog.lastIndex)
        tvActivityLog?.text = activityLog.joinToString("\n")
        tvActivityLog?.setTextColor(0xFF555566.toInt())
    }

    private fun startPingPulse() {
        stopPingPulse()
        pingPulse = object : Runnable {
            var up = true
            override fun run() {
                try {
                    statusRing?.animate()?.alpha(if (up) 0.6f else 0.15f)?.setDuration(900)?.start()
                    up = !up
                    handler.postDelayed(this, 950)
                } catch (_: Exception) {}
            }
        }
        handler.post(pingPulse!!)
    }

    private fun stopPingPulse() {
        pingPulse?.let { handler.removeCallbacks(it) }
        try { statusRing?.alpha = 0.1f } catch (_: Exception) {}
    }

    private fun sendCmd(command: String) {
        (activity as? MainActivity)?.sendCommand(command)
    }
}
