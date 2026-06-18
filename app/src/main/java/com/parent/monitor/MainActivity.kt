package com.parent.monitor

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    var wsManager: WebSocketManager? = null
    private lateinit var adapter: ViewPagerAdapter
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvPing: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvBattery = findViewById(R.id.tvBattery)
        tvPing = findViewById(R.id.tvPing)

        setupViewPager()
        connectWebSocket()
        startPingDisplay()

        findViewById<FloatingActionButton>(R.id.fabSettings).setOnClickListener {
            findViewById<ViewPager2>(R.id.viewPager).currentItem = 2
        }
    }

    private fun setupViewPager() {
        adapter = ViewPagerAdapter(this)
        val vp = findViewById<ViewPager2>(R.id.viewPager)
        vp.adapter = adapter

        val tabs = listOf("🔔 Notifications", "🎮 Control", "⚙️ Settings")
        TabLayoutMediator(
            findViewById(R.id.tabLayout), vp
        ) { tab, pos -> tab.text = tabs[pos] }.attach()
    }

    private fun connectWebSocket() {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val url = prefs.getString("server_url",
            "wss://optimal-inputs-beginners-opt.trycloudflare.com")!!

        wsManager = WebSocketManager(
            serverUrl = url,
            onConnected = { runOnUiThread { setOnline() } },
            onDisconnected = { runOnUiThread { setOffline() } },
            onMessage = { data -> runOnUiThread { handleMessage(data) } }
        )
        wsManager?.connect()
    }

    private fun handleMessage(data: org.json.JSONObject) {
        when (data.optString("type")) {

            "notification" -> {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val item = NotificationItem(
                    app = data.optString("app", "Unknown"),
                    title = data.optString("title", ""),
                    text = data.optString("text", ""),
                    time = sdf.format(Date())
                )
                adapter.notifFragment.adapter.addNotification(item)
                vibrate()
            }

            "battery" -> {
                val level = data.optInt("battery", -1)
                if (level >= 0) {
                    tvBattery.text = "🔋 $level%"
                    adapter.controlFragment.updateBattery(level)
                }
            }

            "location" -> {
                val lat = data.optDouble("lat", 0.0)
                val lng = data.optDouble("lng", 0.0)
                adapter.controlFragment.updateLocation(lat, lng)
            }

            "status" -> {
                val childOnline = data.optBoolean("child_online", false)
                if (childOnline) setChildOnline() else setChildOffline()
            }
        }
    }

    private fun setOnline() {
        statusDot.setBackgroundResource(R.drawable.circle_green)
        tvStatus.text = "🟢 Server Connected"
        tvStatus.setTextColor(getColor(R.color.online_green))
        adapter.settingsFragment.updateInfo("Connected ✓")
    }

    private fun setOffline() {
        statusDot.setBackgroundResource(R.drawable.circle_red)
        tvStatus.text = "🔴 Disconnected"
        tvStatus.setTextColor(getColor(R.color.offline_red))
        tvPing.text = " | 📶 --ms"
        adapter.settingsFragment.updateInfo("Disconnected - Retrying...")
    }

    private fun setChildOnline() {
        tvStatus.text = "🟢 Child Online"
    }

    private fun setChildOffline() {
        tvStatus.text = "🟡 Child Offline"
    }

    private fun startPingDisplay() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                wsManager?.let {
                    if (it.isConnected()) {
                        tvPing.text = " | 📶 ${it.pingMs}ms"
                    }
                }
                handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    private fun vibrate() {
        try {
            val v = getSystemService(android.os.Vibrator::class.java)
            v?.vibrate(android.os.VibrationEffect.createOneShot(
                100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager?.disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
