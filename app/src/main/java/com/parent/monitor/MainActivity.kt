package com.parent.monitor

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // ─── Fragment refs (set by each fragment in onViewCreated) ──────────────
    var dashboardFragment: DashboardFragment? = null
    var liveFragment:      LiveFragment?      = null

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvPing: TextView
    private lateinit var btnNetToggle: Button

    // ─── WebSocket ───────────────────────────────────────────────────────────
    private var ws: WebSocket? = null
    private var client: OkHttpClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pingInterval: Runnable? = null
    private var reconnectRunnable: Runnable? = null
    private var connected = false
    private var pingTs = 0L

    // ─── Prefs ───────────────────────────────────────────────────────────────
    private lateinit var prefs: SharedPreferences
    private var serverUrl = "wss://your-app.replit.app/api/ws"
    private var pairCode  = "123456"

    // ─── State ───────────────────────────────────────────────────────────────
    private var netEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("config", MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", serverUrl) ?: serverUrl
        pairCode  = prefs.getString("pair_code",  pairCode)  ?: pairCode

        bindViews()
        setupViewPager()
        setupNetToggle()
        connect()
    }

    private fun bindViews() {
        tvStatus    = findViewById(R.id.tvStatus)
        tvBattery   = findViewById(R.id.tvBattery)
        tvPing      = findViewById(R.id.tvPing)
        btnNetToggle= findViewById(R.id.btnNetToggle)
    }

    private fun setupViewPager() {
        val pager  = findViewById<ViewPager2>(R.id.viewPager)
        val tabs   = findViewById<TabLayout>(R.id.tabLayout)

        val tabs_list = listOf("Dashboard","Live","Apps","Calls & SMS","Location","Files","Controls","Shizuku")

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs_list.size
            override fun createFragment(pos: Int): Fragment = when (pos) {
                0 -> DashboardFragment()
                1 -> LiveFragment()
                2 -> AppsFragment()
                3 -> CallSmsFragment()
                4 -> LocationFragment()
                5 -> FilesFragment()
                6 -> ControlsFragment()
                7 -> ShizukuFragment()
                else -> DashboardFragment()
            }
        }
        pager.offscreenPageLimit = 2
        TabLayoutMediator(tabs, pager) { tab, pos -> tab.text = tabs_list[pos] }.attach()
    }

    private fun setupNetToggle() {
        btnNetToggle.setOnClickListener {
            netEnabled = !netEnabled
            val cmd = if (netEnabled) "wifi_on" else "wifi_off"
            sendToChild(JSONObject().apply { put("command", cmd) })
            btnNetToggle.text = if (netEnabled) "NET" else "NET✕"
            btnNetToggle.setBackgroundColor(if (netEnabled) 0xFF00E5FF.toInt() else 0xFFFF1744.toInt())
        }
    }

    // ─── WebSocket connection ─────────────────────────────────────────────────

    fun connect() {
        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
        val req = Request.Builder().url(serverUrl).build()
        ws = client!!.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, res: Response) {
                ws.send(JSONObject().apply {
                    put("type", "register"); put("role", "parent"); put("pair_code", pairCode)
                }.toString())
            }
            override fun onMessage(ws: WebSocket, text: String) {
                try { handleMessage(JSONObject(text)) } catch (_: Exception) {}
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) { setConnected(false) }
            override fun onClosed(ws: WebSocket, code: Int, reason: String)  { setConnected(false); scheduleReconnect() }
            override fun onFailure(ws: WebSocket, t: Throwable, res: Response?) { setConnected(false); scheduleReconnect() }
        })
    }

    private fun handleMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "auth_ok" -> handler.post { /* registered */ }
            "peer_connected"    -> setConnected(true)
            "peer_disconnected" -> setConnected(false)
            "pong" -> {
                val ms = System.currentTimeMillis() - pingTs
                handler.post {
                    tvPing.text = "${ms}ms"
                    dashboardFragment?.updatePing(ms)
                }
            }
            "screen_frame"  -> liveFragment?.onScreenFrame(msg.optString("frame"))
            "camera_frame"  -> liveFragment?.onCameraFrame(msg.optString("frame"))
            "battery" -> {
                val pct  = msg.optInt("battery", -1)
                val chg  = msg.optBoolean("charging", false)
                handler.post {
                    val label = "${pct}%${if (chg) "⚡" else ""}"
                    tvBattery.text = label
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
        }
    }

    private fun setConnected(on: Boolean) {
        connected = on
        handler.post {
            tvStatus.text  = if (on) "Online" else "Offline"
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

    fun sendToChild(cmd: JSONObject) {
        try { ws?.send(cmd.toString()) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopPinging()
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        ws?.close(1000, null)
        super.onDestroy()
    }
}
