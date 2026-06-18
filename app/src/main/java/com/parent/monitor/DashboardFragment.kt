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

class DashboardFragment : Fragment() {

    private lateinit var tvChildStatus: TextView
    private lateinit var tvLastSeen: TextView
    private lateinit var tvDashCurrentApp: TextView
    private lateinit var tvDashBattery: TextView
    private lateinit var tvDashPing: TextView
    private lateinit var tvDashLocation: TextView
    private lateinit var bigStatusDot: View
    private lateinit var statusRing: View
    private lateinit var btnDashLock: Button
    private lateinit var btnDashCamera: Button
    private lateinit var btnDashLocation: Button
    private lateinit var btnDashBattery: Button
    private lateinit var btnDashCurrentApp: Button
    private lateinit var btnDashGrantPerms: Button

    private val handler = Handler(Looper.getMainLooper())
    private var pingPulse: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvChildStatus    = view.findViewById(R.id.tvChildStatus)
        tvLastSeen       = view.findViewById(R.id.tvLastSeen)
        tvDashCurrentApp = view.findViewById(R.id.tvDashCurrentApp)
        tvDashBattery    = view.findViewById(R.id.tvDashBattery)
        tvDashPing       = view.findViewById(R.id.tvDashPing)
        tvDashLocation   = view.findViewById(R.id.tvDashLocation)
        bigStatusDot     = view.findViewById(R.id.bigStatusDot)
        statusRing       = view.findViewById(R.id.statusRing)
        btnDashLock      = view.findViewById(R.id.btnDashLock)
        btnDashCamera    = view.findViewById(R.id.btnDashCamera)
        btnDashLocation  = view.findViewById(R.id.btnDashLocation)
        btnDashBattery   = view.findViewById(R.id.btnDashBattery)
        btnDashCurrentApp= view.findViewById(R.id.btnDashCurrentApp)
        btnDashGrantPerms= view.findViewById(R.id.btnDashGrantPerms)

        (activity as? MainActivity)?.dashboardFragment = this

        btnDashLock.setOnClickListener      { sendCommand(JSONObject().apply { put("command", "lock_screen") }) }
        btnDashCamera.setOnClickListener    { sendCommand(JSONObject().apply { put("command", "take_photo") }) }
        btnDashLocation.setOnClickListener  { sendCommand(JSONObject().apply { put("command", "get_location") }) }
        btnDashBattery.setOnClickListener   { sendCommand(JSONObject().apply { put("command", "get_battery") }) }
        btnDashCurrentApp.setOnClickListener{ sendCommand(JSONObject().apply { put("command", "get_running_app") }) }
        btnDashGrantPerms.setOnClickListener{ sendCommand(JSONObject().apply { put("command", "grant_permissions") }) }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.dashboardFragment = null
        super.onDestroyView()
    }

    fun updateOnline(online: Boolean) {
        if (!isAdded) return
        if (online) {
            tvChildStatus.text = "CHILD ONLINE"
            tvChildStatus.setTextColor(0xFF00C853.toInt())
            bigStatusDot.setBackgroundColor(0xFF00C853.toInt())
            statusRing.setBackgroundColor(0xFF00C853.toInt())
            tvLastSeen.text = "Connected just now"
            startPingPulse()
        } else {
            tvChildStatus.text = "CHILD OFFLINE"
            tvChildStatus.setTextColor(0xFFF44336.toInt())
            bigStatusDot.setBackgroundColor(0xFFF44336.toInt())
            statusRing.setBackgroundColor(0xFFF44336.toInt())
            stopPingPulse()
        }
    }

    fun updateBattery(pct: Int, charging: Boolean) {
        if (!isAdded) return
        val label = "${pct}%"
        tvDashBattery.text = label
        tvDashBattery.setTextColor(when {
            pct > 50  -> 0xFF00C853.toInt()
            pct > 20  -> 0xFFFFD600.toInt()
            else      -> 0xFFFF1744.toInt()
        })
    }

    fun updatePing(ms: Long) {
        if (!isAdded) return
        tvDashPing.text = "${ms}ms"
        tvDashPing.setTextColor(when {
            ms < 150  -> 0xFF00C853.toInt()
            ms < 400  -> 0xFFFFD600.toInt()
            else      -> 0xFFFF1744.toInt()
        })
    }

    fun updateLocation(lat: Double, lng: Double) {
        if (!isAdded) return
        tvDashLocation.text = "%.4f, %.4f".format(lat, lng)
    }

    fun updateCurrentApp(pkg: String) {
        if (!isAdded) return
        val name = pkg.substringAfterLast('.')
        tvDashCurrentApp.text = "Using: $name"
    }

    private fun startPingPulse() {
        pingPulse = object : Runnable {
            var up = true
            override fun run() {
                try {
                    statusRing.animate().alpha(if (up) 0.5f else 0.1f).setDuration(800).start()
                    up = !up
                    handler.postDelayed(this, 900)
                } catch (_: Exception) {}
            }
        }
        handler.post(pingPulse!!)
    }

    private fun stopPingPulse() {
        pingPulse?.let { handler.removeCallbacks(it) }
        try { statusRing.alpha = 0.2f } catch (_: Exception) {}
    }

    private fun sendCommand(cmd: JSONObject) { (activity as? MainActivity)?.sendToChild(cmd) }
}
