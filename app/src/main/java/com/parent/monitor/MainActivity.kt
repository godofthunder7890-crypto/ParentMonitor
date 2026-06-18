package com.parent.monitor

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    var wsManager: WebSocketManager? = null
    lateinit var adapter: ViewPagerAdapter
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvPing: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var wifiOn = true

    companion object {
        const val PREFS_NAME     = "config"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_PAIR_CODE  = "pair_code"
        const val DEFAULT_URL    = "wss://your-app.replit.app/api/ws"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        tvStatus  = findViewById(R.id.tvStatus)
        tvBattery = findViewById(R.id.tvBattery)
        tvPing    = findViewById(R.id.tvPing)

        setupViewPager()
        connectWebSocket()
        startPingTicker()

        findViewById<Button>(R.id.btnNetToggle).setOnClickListener {
            if (wifiOn) {
                wsManager?.sendCommand("wifi_off")
                (it as Button).text = "NET OFF"
                it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4444.toInt())
                it.setTextColor(0xFFFFFFFF.toInt())
            } else {
                wsManager?.sendCommand("wifi_on")
                (it as Button).text = "NET ON"
                it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt())
                it.setTextColor(0xFF000000.toInt())
            }
            wifiOn = !wifiOn
        }
    }

    private fun setupViewPager() {
        adapter = ViewPagerAdapter(this)
        val vp = findViewById<ViewPager2>(R.id.viewPager)
        vp.adapter = adapter
        vp.offscreenPageLimit = 8
        val tabs = listOf("📊 Dash", "📹 Live", "🛡️ Protect", "⏱️ Limits", "📍 Track", "📂 Data", "📋 Reports", "🔔 Alerts", "⚙️ Settings")
        TabLayoutMediator(
            findViewById(R.id.tabLayout), vp
        ) { tab, pos -> tab.text = tabs[pos] }.attach()
    }

    fun connectWebSocket() {
        val prefs    = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url      = prefs.getString(KEY_SERVER_URL, DEFAULT_URL)!!
        val pairCode = prefs.getString(KEY_PAIR_CODE, "")!!

        wsManager?.disconnect()
        wsManager = WebSocketManager(
            serverUrl      = url,
            pairCode       = pairCode,
            onConnected    = { runOnUiThread { setServerOnline() } },
            onDisconnected = { runOnUiThread { setServerOffline() } },
            onMessage      = { data -> runOnUiThread { handleMessage(data) } }
        )
        wsManager?.connect()
    }

    private fun handleMessage(data: JSONObject) {
        when (data.optString("type")) {

            // ── Connection / Status ─────────────────────────────────────
            "status" -> {
                if (data.optBoolean("child_online"))
                    adapter.dashboardFragment.setChildOnline()
                else
                    adapter.dashboardFragment.setChildOffline()
            }

            // ── Battery ─────────────────────────────────────────────────
            "battery" -> {
                val lvl = data.optInt("battery", -1)
                if (lvl >= 0) {
                    tvBattery.text = "🔋$lvl%"
                    adapter.dashboardFragment.updateBattery(lvl)
                }
            }

            // ── Location ─────────────────────────────────────────────────
            "location" -> {
                val lat = data.optDouble("lat")
                val lng = data.optDouble("lng")
                val acc = data.optDouble("accuracy", 0.0).toFloat()
                val inside = if (data.has("geofence_inside")) data.optBoolean("geofence_inside") else null
                adapter.dashboardFragment.updateLocation(lat, lng)
                adapter.trackFragment.onLocationUpdate(lat, lng, acc, inside)
            }

            // ── Geofence ─────────────────────────────────────────────────
            "geofence_alert" -> {
                val inside = data.optBoolean("inside")
                val dist   = data.optInt("dist_m")
                adapter.trackFragment.onGeofenceAlert(inside, dist)
                adapter.reportsFragment.addAlert("GEOFENCE",
                    if (inside) "Returned inside safe zone" else "⚠️ LEFT SAFE ZONE (${dist}m away)")
                vibrate()
            }

            // ── SOS ───────────────────────────────────────────────────────
            "sos_alert", "sos_location" -> {
                adapter.trackFragment.onSosAlert()
                adapter.reportsFragment.addAlert("🚨 SOS", "Child triggered panic button!")
                vibrate(3)
            }

            // ── App events ────────────────────────────────────────────────
            "current_app", "app_open" -> {
                val pkg = data.optString("package")
                adapter.dashboardFragment.updateCurrentApp(pkg)
                adapter.dashboardFragment.setChildOnline()
            }

            // ── App Blocked ───────────────────────────────────────────────
            "app_blocked" -> {
                val pkg    = data.optString("package")
                val reason = data.optString("reason")
                adapter.protectFragment.onAppBlocked(pkg, reason)
                adapter.reportsFragment.addAlert("BLOCKED",
                    "${pkg.substringAfterLast('.')} ($reason)")
            }

            // ── Device info (on connect) ──────────────────────────────────
            "device_info" -> {
                adapter.dashboardFragment.setChildOnline()
                adapter.protectFragment.onDeviceInfo(data)
            }

            // ── Keyword Alert ─────────────────────────────────────────────
            "keyword_alert" -> {
                val kw  = data.optString("keyword")
                val pkg = data.optString("package")
                val src = data.optString("source")
                adapter.reportsFragment.addAlert("🔍 KEYWORD",
                    "'$kw' in ${pkg.substringAfterLast('.')} ($src)")
                vibrate()
            }

            // ── Schedule event ────────────────────────────────────────────
            "schedule_event" -> {
                val action = data.optString("action")
                val hour   = data.optInt("hour")
                adapter.limitsFragment.onScheduleEvent(action, hour)
                adapter.reportsFragment.addAlert("SCHEDULE", "$action at $hour:00")
            }

            // ── Daily Report ──────────────────────────────────────────────
            "daily_report" -> {
                adapter.reportsFragment.onDailyReport(data)
            }

            // ── Live streams ──────────────────────────────────────────────
            "screen_frame" -> {
                adapter.dashboardFragment.setChildOnline()
                adapter.liveFragment.onScreenFrame(data.optString("frame"))
            }
            "camera_frame" -> {
                adapter.dashboardFragment.setChildOnline()
                adapter.liveFragment.onCameraFrame(data.optString("frame"))
            }
            "photo" -> adapter.liveFragment.onCameraFrame(data.optString("image"))

            // ── Data ──────────────────────────────────────────────────────
            "gallery"  -> adapter.dataFragment.onGallery(data.optJSONArray("photos") ?: return)
            "call_log" -> {
                adapter.dataFragment.onList("Calls", data.optJSONArray("calls") ?: return) { c ->
                    val t = c.optString("type")[0].uppercaseChar()
                    val dur = c.optLong("duration")
                    "[$t] ${c.optString("number")}  ${c.optString("name")}  ${dur}s"
                }
            }
            "sms" -> {
                adapter.dataFragment.onList("SMS", data.optJSONArray("messages") ?: return) { m ->
                    val t = if (m.optString("type") == "inbox") "📥" else "📤"
                    "$t ${m.optString("from")}  ${m.optString("body").take(60)}"
                }
            }
            "app_usage" -> {
                adapter.dataFragment.onList("Apps", data.optJSONArray("stats") ?: return) { s ->
                    val mins = s.optLong("totalTime") / 60000
                    "${s.optString("package").substringAfterLast('.')}  — ${mins}min"
                }
            }

            // ── Notifications / Chat ──────────────────────────────────────
            "notification" -> {
                val item = NotificationItem(
                    app   = data.optString("app", "?"),
                    title = data.optString("title"),
                    text  = data.optString("text"),
                    time  = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()))
                adapter.notifFragment.addNotification(item)
                vibrate()
            }
            "chat_content" -> {
                val pkg  = data.optString("package").substringAfterLast('.')
                val content = data.optString("content").take(200)
                val item = NotificationItem(app = pkg, title = "Chat",
                    text = content,
                    time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date()))
                adapter.notifFragment.addNotification(item)
            }

            // ── Permissions ───────────────────────────────────────────────
            "permissions_result" -> {
                val count = data.optInt("granted")
                val shizuku = data.optBoolean("shizuku_available")
                tvStatus.text = if (shizuku) "🔑 $count perms (Shizuku)" else "⚠️ Shizuku unavailable"
            }
        }
    }

    private fun setServerOnline() {
        statusDot.setBackgroundResource(R.drawable.circle_green)
        tvStatus.text = "● Connected"
        tvStatus.setTextColor(0xFF4CAF50.toInt())
    }

    private fun setServerOffline() {
        statusDot.setBackgroundResource(R.drawable.circle_red)
        tvStatus.text = "● Offline"
        tvStatus.setTextColor(0xFFF44336.toInt())
        tvPing.text = "📶--ms"
        adapter.dashboardFragment.setChildOffline()
    }

    private fun startPingTicker() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                wsManager?.let { ws ->
                    if (ws.isConnected()) {
                        tvPing.text = "📶${ws.pingMs}ms"
                        adapter.dashboardFragment.updatePing(ws.pingMs)
                    }
                }
                handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    private fun vibrate(times: Int = 1) {
        try {
            val v = getSystemService(android.os.Vibrator::class.java)
            repeat(times) { n ->
                handler.postDelayed({
                    v?.vibrate(android.os.VibrationEffect.createOneShot(
                        150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                }, n * 300L)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager?.disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
