package com.parent.monitor

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        const val DEFAULT_URL      = "wss://ws-relay-production-efbb.up.railway.app/api/ws"
    }

    // ─── Fragment refs ────────────────────────────────────────────────────────
    var dashboardFragment:       DashboardFragment?       = null
    var liveFragment:            LiveFragment?            = null
    var appsFragment:            AppsFragment?            = null
    var callSmsFragment:         CallSmsFragment?         = null
    var locationFragment:        LocationFragment?        = null
    var filesFragment:           FilesFragment?           = null
    var notificationsFragment:   NotificationsFragment?   = null
    var limitsFragment:          LimitsFragment?          = null
    var protectFragment:         ProtectFragment?         = null
    var trackFragment:           TrackFragment?           = null
    var reportsFragment:         ReportsFragment?         = null
    var dataFragment:            DataFragment?            = null
    // ── New fragments (blueprint missing features) ────────────────────────────
    var browserSafetyFragment:   BrowserSafetyFragment?  = null
    var videoHistoryFragment:    VideoHistoryFragment?    = null
    var recordingsFragment:      RecordingsFragment?      = null
    var albumsSafetyFragment:    AlbumsSafetyFragment?   = null

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
        val tabTitles = listOf(
            "Dashboard","Live","Apps","Calls","Location","Files","Notifs",
            "Limits","Protect","Track","Reports","Data","Controls","Shizuku",
            "Browser","Videos","Recordings","Albums","Settings"
        )

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabTitles.size
            override fun createFragment(pos: Int): Fragment = when (pos) {
                0  -> DashboardFragment()
                1  -> LiveFragment()
                2  -> AppsFragment()
                3  -> CallSmsFragment()
                4  -> LocationFragment()
                5  -> FilesFragment()
                6  -> NotificationsFragment()
                7  -> LimitsFragment()
                8  -> ProtectFragment()
                9  -> TrackFragment()
                10 -> ReportsFragment()
                11 -> DataFragment()
                12 -> ControlFragment()
                13 -> ShizukuFragment()
                14 -> BrowserSafetyFragment()
                15 -> VideoHistoryFragment()
                16 -> RecordingsFragment()
                17 -> AlbumsSafetyFragment()
                18 -> SettingsFragment()
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
                val lat = msg.optDouble("lat")
                val lng = msg.optDouble("lng")
                val acc = msg.optDouble("accuracy", 0.0).toFloat()
                val geoInside = if (msg.has("geofence_inside")) msg.optBoolean("geofence_inside") else null
                handler.post {
                    dashboardFragment?.updateLocation(lat, lng)
                    locationFragment?.onLocation(lat, lng, acc)
                    trackFragment?.onLocationUpdate(lat, lng, acc, geoInside)
                }
            }
            "current_app" -> {
                val pkg = msg.optString("package")
                handler.post { dashboardFragment?.updateCurrentApp(pkg) }
            }
            "app_usage" -> {
                val arr = msg.optJSONArray("stats")
                if (arr != null) handler.post {
                    appsFragment?.onData(arr)
                    dataFragment?.onList("Apps", arr) { obj ->
                        val name = obj.optString("package").substringAfterLast('.')
                        val mins = obj.optLong("minutes", 0)
                        "$name — ${mins}min"
                    }
                }
            }
            "call_log" -> {
                val arr = msg.optJSONArray("calls")
                if (arr != null) handler.post {
                    callSmsFragment?.onCallData(arr)
                    dataFragment?.onList("Calls", arr) { obj ->
                        val num  = obj.optString("number", "Unknown")
                        val type = obj.optString("type", "")
                        val dur  = obj.optInt("duration", 0)
                        "$num  [$type]  ${dur}s"
                    }
                }
            }
            "sms" -> {
                val arr = msg.optJSONArray("messages")
                if (arr != null) handler.post {
                    callSmsFragment?.onSmsData(arr)
                    dataFragment?.onList("SMS", arr) { obj ->
                        val addr = obj.optString("address", "Unknown")
                        val body = obj.optString("body", "").take(60)
                        "$addr: $body"
                    }
                }
            }
            "gallery" -> {
                val arr = msg.optJSONArray("photos")
                if (arr != null) handler.post {
                    filesFragment?.onFiles(arr)
                    dataFragment?.onGallery(arr)
                }
            }
            "notification" -> {
                val ts = msg.optLong("ts", System.currentTimeMillis())
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
                val item = NotificationItem(
                    app   = msg.optString("app"),
                    title = msg.optString("title"),
                    text  = msg.optString("text"),
                    time  = timeStr,
                    timestamp = ts
                )
                handler.post { notificationsFragment?.addNotification(item) }
            }
            "update_result" -> {
                val ok  = msg.optBoolean("success")
                val ver = msg.optString("version", "")
                val err = msg.optString("error", "")
                handler.post {
                    dashboardFragment?.addLog(
                        if (ok) "Child updated to $ver successfully!"
                        else    "Update failed: $err"
                    )
                }
            }
            // ── New fragment routing ──────────────────────────────────────────
            "daily_report" -> handler.post { reportsFragment?.onDailyReport(msg) }
            "app_blocked" -> {
                val pkg    = msg.optString("package")
                val reason = msg.optString("reason")
                handler.post {
                    protectFragment?.onAppBlocked(pkg, reason)
                    reportsFragment?.addAlert("App Blocked", "${pkg.substringAfterLast('.')} ($reason)")
                    dashboardFragment?.addLog("🚫 Blocked: ${pkg.substringAfterLast('.')} ($reason)")
                }
            }
            "geofence_alert" -> {
                val inside = msg.optBoolean("inside")
                val distM  = msg.optInt("dist_m")
                handler.post { trackFragment?.onGeofenceAlert(inside, distM) }
            }
            "schedule_event" -> {
                val action = msg.optString("action")
                val hour   = msg.optInt("hour")
                handler.post { limitsFragment?.onScheduleEvent(action, hour) }
            }
            "keyword_alert" -> {
                val keyword = msg.optString("keyword")
                val pkg     = msg.optString("package")
                val source  = msg.optString("source")
                handler.post {
                    reportsFragment?.addAlert("Keyword [$source]", "\"$keyword\" in ${pkg.substringAfterLast('.')}")
                    dashboardFragment?.addLog("🔍 Keyword \"$keyword\" detected in ${pkg.substringAfterLast('.')}")
                }
            }
            "device_info" -> handler.post { protectFragment?.onDeviceInfo(msg) }
            "app_open" -> {
                val pkg = msg.optString("package")
                handler.post {
                    reportsFragment?.addAlert("App Opened", pkg.substringAfterLast('.'))
                }
            }
            "chat_content" -> {
                val pkg     = msg.optString("package")
                val content = msg.optString("content").take(80)
                handler.post {
                    reportsFragment?.addAlert("Chat [${pkg.substringAfterLast('.')}]", content)
                }
            }

            // ── New events from blueprint features ────────────────────────────
            "browser_blocked" -> {
                val url    = msg.optString("url")
                val domain = msg.optString("domain")
                val pkg    = msg.optString("package")
                handler.post {
                    browserSafetyFragment?.onBrowserBlocked(url, domain, pkg)
                    reportsFragment?.addAlert("🌐 Browser Blocked", "$domain\n${url.take(50)}")
                    dashboardFragment?.addLog("🌐 Blocked browser: $domain")
                }
            }
            "blocked_domains" -> {
                val arr = msg.optJSONArray("domains")
                if (arr != null) {
                    val list = (0 until arr.length()).map { arr.getString(it) }
                    handler.post { browserSafetyFragment?.onBlockedDomains(list) }
                }
            }
            "video_history" -> {
                handler.post { videoHistoryFragment?.onVideoHistory(msg) }
            }
            "video_watched" -> {
                handler.post {
                    val title    = msg.optString("title", "")
                    val platform = msg.optString("package", "").let {
                        if (it.contains("youtube")) "YouTube" else "TikTok"
                    }
                    val secs     = msg.optLong("watched_seconds", 0)
                    reportsFragment?.addAlert("$platform watched", "${secs}s — $title")
                    videoHistoryFragment?.onVideoHistory(msg)
                }
            }
            "permission_revoked" -> {
                val perm = msg.optString("permission")
                handler.post {
                    reportsFragment?.addAlert("⚠️ Permission OFF!", perm)
                    dashboardFragment?.addLog("⚠️ Permission revoked: $perm")
                }
            }
            "permission_status" -> {
                handler.post { dashboardFragment?.addLog("🔐 Perms: ${msg.toString().take(80)}") }
            }
            "recording_started" -> {
                val type = msg.optString("type")
                handler.post { dashboardFragment?.addLog("🎙 Recording started: $type") }
            }
            "recording_ready" -> {
                handler.post { recordingsFragment?.onRecordingReady(msg) }
            }
            "recording_chunk" -> {
                handler.post { recordingsFragment?.onRecordingChunk(msg) }
            }
            "recording_list" -> {
                val arr = msg.optJSONArray("files")
                if (arr != null) handler.post { recordingsFragment?.onRecordingList(arr) }
            }
            "back_online" -> {
                val mins = msg.optLong("offline_minutes", 0)
                handler.post {
                    dashboardFragment?.addLog("📶 Child back online (was offline ${mins}min)")
                    reportsFragment?.addAlert("📶 Back Online", "Was offline for ${mins} minutes")
                }
            }

            // ── Albums Safety ─────────────────────────────────────────────────
            "albums_scan_result" -> {
                handler.post {
                    albumsSafetyFragment?.onScanResult(msg)
                    val count = msg.optInt("flagged_count", 0)
                    if (count > 0) {
                        reportsFragment?.addAlert("🖼 Albums Scan", "$count suspicious images found!")
                        dashboardFragment?.addLog("🖼 Albums: $count suspicious images flagged")
                    } else {
                        dashboardFragment?.addLog("🖼 Albums: clean, no suspicious images")
                    }
                }
            }
            "album_full_image" -> handler.post { albumsSafetyFragment?.onAlbumFullImage(msg) }

            // ── Camera Recording ──────────────────────────────────────────────
            "camera_record_started" -> {
                val fn = msg.optString("filename", "")
                handler.post { dashboardFragment?.addLog("🎥 Camera recording: $fn") }
            }
            "camera_record_done" -> {
                val fn   = msg.optString("filename", "")
                val size = msg.optLong("size_kb", 0)
                handler.post {
                    dashboardFragment?.addLog("🎥 Camera record done: $fn (${size}KB)")
                    reportsFragment?.addAlert("🎥 Camera Recording Ready", "$fn — ${size}KB")
                }
            }
            "camera_record_list" -> {
                val arr = msg.optJSONArray("files")
                if (arr != null) handler.post { recordingsFragment?.onRecordingList(arr) }
            }
            "camera_record_chunk" -> handler.post { recordingsFragment?.onRecordingChunk(msg) }

            // ── Screen Recording ──────────────────────────────────────────────
            "screen_record_started" -> {
                val fn = msg.optString("filename", "")
                handler.post { dashboardFragment?.addLog("📱 Screen recording: $fn") }
            }
            "screen_record_done" -> {
                val fn   = msg.optString("filename", "")
                val size = msg.optLong("size_kb", 0)
                handler.post {
                    dashboardFragment?.addLog("📱 Screen record done: $fn (${size}KB)")
                    reportsFragment?.addAlert("📱 Screen Recording Ready", "$fn — ${size}KB")
                }
            }
            "screen_record_list"  -> {
                val arr = msg.optJSONArray("files")
                if (arr != null) handler.post { recordingsFragment?.onRecordingList(arr) }
            }
            "screen_record_chunk" -> handler.post { recordingsFragment?.onRecordingChunk(msg) }

            // ── Emergency Lock / Unlock ────────────────────────────────────────
            "emergency_locked_all" -> {
                val count = msg.optInt("blocked_count", 0)
                handler.post {
                    dashboardFragment?.addLog("🚨 Emergency locked! $count apps blocked")
                    reportsFragment?.addAlert("🚨 Emergency Lock", "$count apps blocked on child device")
                }
            }
            "emergency_unlocked_all" -> {
                handler.post {
                    dashboardFragment?.addLog("✅ Emergency lock released — all apps unblocked")
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
