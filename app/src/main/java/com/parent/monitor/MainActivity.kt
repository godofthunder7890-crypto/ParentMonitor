package com.parent.monitor

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
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
        const val DEFAULT_URL      = "wss://relay-server-production-bf46.up.railway.app/api/ws"

        // Bottom nav → ViewPager tab indices
        private const val TAB_DASHBOARD    = 0
        private const val TAB_LIVE         = 1
        private const val TAB_LIMITS       = 7
        private const val TAB_PROTECT      = 8
        private const val TAB_REPORTS      = 10
        private const val TAB_SETTINGS     = 19
        private const val TAB_TIME_REQUEST = 20
        private const val TAB_CALL_SAFETY  = 21
        private const val TAB_AI_INSIGHTS = 22
        private const val TAB_MESSAGES    = 23
    }

    val notificationAdapter = NotificationAdapter()

    // ─── Fragment refs ────────────────────────────────────────────────────────
    var dashboardFragment:       DashboardFragment?       = null
    var controlFragment:         ControlFragment?         = null
      var lastDeviceW = 0
      var lastDeviceH = 0
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
    var browserSafetyFragment:   BrowserSafetyFragment?  = null
    var videoHistoryFragment:    VideoHistoryFragment?    = null
    var recordingsFragment:      RecordingsFragment?      = null
    var albumsSafetyFragment:    AlbumsSafetyFragment?   = null
    // New fragments
    var timeRequestFragment:     TimeRequestFragment?     = null
    var callWhitelistFragment:   CallWhitelistFragment?   = null
    var aiInsightsFragment:      AiInsightsFragment?      = null
    var messagesFragment:        MessagesFragment?         = null

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var tvStatus:         TextView
    private lateinit var tvBattery:        TextView
    private lateinit var tvPing:           TextView
    private lateinit var btnNetToggle:     Button
    private lateinit var bottomNav:        BottomNavigationView
    private lateinit var bannerTimeRequest: LinearLayout
    private lateinit var viewPager:        ViewPager2

    // ─── WebSocket ───────────────────────────────────────────────────────────
    private var ws:     WebSocket?    = null
    private var client: OkHttpClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var pingInterval:      Runnable? = null
    private var keepaliveRunnable: Runnable? = null
    private var connected = false
    private var reconnectDelay = 5_000L
    var reconnectCount = 0
    private var pingTs    = 0L

    // ─── Prefs ───────────────────────────────────────────────────────────────
    private lateinit var prefs: SharedPreferences
    private var serverUrl = DEFAULT_URL
    private var pairCode  = ""
    private var netEnabled = true

    inner class WsCompat {
        fun sendCommand(cmd: String) = this@MainActivity.sendCommand(cmd)
        fun sendCommandObj(data: JSONObject) = this@MainActivity.sendCommandObj(data)
        fun isConnected() = connected
    }
    val wsManager = WsCompat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        UpdateWorker.schedule(this)

        prefs     = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        pairCode  = prefs.getString(KEY_PAIR_CODE, "") ?: ""
        if (pairCode.isEmpty()) {
            pairCode = (100000..999999).random().toString()
            prefs.edit().putString(KEY_PAIR_CODE, pairCode).apply()
        }
        // Sync pair code from Firebase (PairSetupActivity saves it there)
        try {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .reference.child("users").child(uid).child("pairCode")
                    .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                        override fun onDataChange(snap: com.google.firebase.database.DataSnapshot) {
                            val fbCode = snap.getValue(String::class.java)
                            if (!fbCode.isNullOrEmpty() && fbCode != pairCode) {
                                pairCode = fbCode
                                prefs.edit().putString(KEY_PAIR_CODE, fbCode).apply()
                                reconnect(serverUrl, fbCode)
                            }
                        }
                        override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
                    })
            }
        } catch (_: Exception) {}

        bindViews()
        setupViewPager()
        setupBottomNav()
        setupTimeRequestBanner()
        setupNetToggle()
        connect()
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 201)
    }

    override fun onRequestPermissionsResult(reqCode: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(reqCode, perms, results)
    }

    private fun bindViews() {
        tvStatus          = findViewById(R.id.tvStatus)
        tvBattery         = findViewById(R.id.tvBattery)
        tvPing            = findViewById(R.id.tvPing)
        btnNetToggle      = findViewById(R.id.btnNetToggle)
        bottomNav         = findViewById(R.id.bottomNav)
        bannerTimeRequest = findViewById(R.id.bannerTimeRequest)
        viewPager         = findViewById(R.id.viewPager)
    }

    private fun setupViewPager() {
        val tabs  = findViewById<TabLayout>(R.id.tabLayout)
        val tabTitles = listOf(
            "Dashboard","Live","Apps","Calls","Location","Files","Notifs",
            "Limits","Protect","Track","Reports","Data","Controls","Shizuku",
            "Browser","Videos","Recordings","Albums","Painting","Settings",
            "TimeReq","CallSafety","AI","Messages"
        )

        viewPager.adapter = object : FragmentStateAdapter(this) {
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
                18 -> PaintingFragment()
                19 -> SettingsFragment()
                20 -> TimeRequestFragment()
                21 -> CallWhitelistFragment()
                22 -> AiInsightsFragment()
                23 -> MessagesFragment()
                else -> DashboardFragment()
            }
        }
        viewPager.offscreenPageLimit = 2
        // Tab strip is hidden (bottom nav is primary) — attach for keyboard navigation
        TabLayoutMediator(tabs, viewPager) { tab, pos -> tab.text = tabTitles[pos] }.attach()
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> { viewPager.currentItem = TAB_DASHBOARD;    true }
                R.id.nav_live     -> { viewPager.currentItem = TAB_LIVE;         true }
                R.id.nav_protect  -> { viewPager.currentItem = TAB_PROTECT;      true }
                R.id.nav_requests -> { viewPager.currentItem = TAB_TIME_REQUEST; true }
                R.id.nav_settings -> { viewPager.currentItem = TAB_SETTINGS;     true }
                else -> false
            }
        }
        // Sync bottom nav when user swipes pager
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = when (position) {
                    TAB_DASHBOARD                         -> R.id.nav_home
                    TAB_LIVE                              -> R.id.nav_live
                    TAB_LIMITS, 8, 14, 17, TAB_CALL_SAFETY -> R.id.nav_protect
                    TAB_TIME_REQUEST                      -> R.id.nav_requests
                    TAB_SETTINGS                          -> R.id.nav_settings
                    else                                  -> R.id.nav_home
                }
                if (bottomNav.selectedItemId != itemId)
                    bottomNav.selectedItemId = itemId
            }
        })
    }

    private fun setupTimeRequestBanner() {
        val btnView = findViewById<Button>(R.id.btnBannerView)
        btnView.setOnClickListener {
            viewPager.currentItem = TAB_TIME_REQUEST
            bottomNav.selectedItemId = R.id.nav_requests
        }
    }

    fun hideBannerIfNoPending() {
        handler.post {
            val hasPending = timeRequestFragment?.hasPendingRequests() == true
            bannerTimeRequest.visibility = if (hasPending) View.VISIBLE else View.GONE
        }
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
                handler.post { notificationAdapter.addNotification(item) }
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

            // ── Screen Time Request (child → parent) ──────────────────────────
            "time_request" -> {
                val requestId = msg.optString("request_id", "req_${System.currentTimeMillis()}")
                val minutes   = msg.optInt("minutes", 30)
                val reason    = msg.optString("reason", "")
                val ts        = msg.optLong("ts", System.currentTimeMillis())
                val req = TimeRequest(
                    requestId = requestId,
                    minutes   = minutes,
                    reason    = reason,
                    timestamp = ts
                )
                handler.post {
                    // Show banner
                    bannerTimeRequest.visibility = View.VISIBLE
                    timeRequestFragment?.onTimeRequest(req)
                    dashboardFragment?.addLog("⏱ Child requests $minutes min extra screen time", 0xFFFFB300.toInt())
                    reportsFragment?.addAlert("⏱ Time Request", "$minutes min requested — reason: $reason")
                    showUrgentNotification("Screen Time Request", "Child wants $minutes min extra. Open app to approve.")
                }
            }

            // ── Call list sync (child → parent) ───────────────────────────────
            "call_lists" -> {
                val whiteArr = msg.optJSONArray("whitelist")
                val blackArr = msg.optJSONArray("blacklist")
                handler.post { callWhitelistFragment?.onCallLists(whiteArr, blackArr) }
            }

            // ── Browser list sync ─────────────────────────────────────────────
            "browser_lists" -> {
                val blockedArr = msg.optJSONArray("blocked")
                val allowedArr = msg.optJSONArray("allowed")
                if (blockedArr != null) {
                    val list = (0 until blockedArr.length()).map { blockedArr.getString(it) }
                    handler.post { browserSafetyFragment?.onBlockedDomains(list) }
                }
                if (allowedArr != null) {
                    val list = (0 until allowedArr.length()).map { allowedArr.getString(it) }
                    handler.post { browserSafetyFragment?.onAllowedDomains(list) }
                }
            }

            // ── Legacy blocked_domains (backward compat) ──────────────────────
            "blocked_domains" -> {
                val arr = msg.optJSONArray("domains")
                if (arr != null) {
                    val list = (0 until arr.length()).map { arr.getString(it) }
                    handler.post { browserSafetyFragment?.onBlockedDomains(list) }
                }
            }

            // ── Existing fragment routing ─────────────────────────────────────
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
            "device_info" -> handler.post {
                protectFragment?.onDeviceInfo(msg)
                val w = msg.optInt("screen_width", 0)
                val h = msg.optInt("screen_height", 0)
                if (w > 0 && h > 0) controlFragment?.onDeviceInfo(w, h)
            }
            "app_open" -> {
                val pkg = msg.optString("package")
                handler.post { reportsFragment?.addAlert("App Opened", pkg.substringAfterLast('.')) }
            }
            "chat_content" -> {
                val pkg     = msg.optString("package")
                val content = msg.optString("content").take(80)
                handler.post { reportsFragment?.addAlert("Chat [${pkg.substringAfterLast('.')}]", content) }
            }
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
            "video_history" -> handler.post { videoHistoryFragment?.onVideoHistory(msg) }
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
            "recording_ready"  -> handler.post { recordingsFragment?.onRecordingReady(msg) }
            "recording_chunk"  -> handler.post { recordingsFragment?.onRecordingChunk(msg) }
            "recording_list"   -> {
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
            "camera_record_list"  -> {
                val arr = msg.optJSONArray("files")
                if (arr != null) handler.post { recordingsFragment?.onRecordingList(arr) }
            }
            "camera_record_chunk" -> handler.post { recordingsFragment?.onRecordingChunk(msg) }
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
            "emergency_locked_all" -> {
                val count = msg.optInt("blocked_count", 0)
                handler.post {
                    dashboardFragment?.addLog("🚨 Emergency locked! $count apps blocked")
                    reportsFragment?.addAlert("🚨 Emergency Lock", "$count apps blocked on child device")
                }
            }
            "paint_ack" -> { }
            "app_list" -> {
                val arr = msg.optJSONArray("apps")
                if (arr != null) handler.post { protectFragment?.onAppList(arr) }
            }
            "sos_activated" -> handler.post {
                dashboardFragment?.addLog("🚨 SOS ACTIVATED on child device!", 0xFFFF1744.toInt())
                dashboardFragment?.setSosActive(true)
                reportsFragment?.addAlert("🚨 EMERGENCY SOS", "Child activated SOS — check immediately!")
                showUrgentNotification("SOS Activated", "Child activated emergency SOS")
            }
            "sos_stopped" -> handler.post {
                dashboardFragment?.addLog("✅ SOS stopped on child device", 0xFF00C853.toInt())
                dashboardFragment?.setSosActive(false)
            }
            "uninstall_attempt" -> handler.post {
                dashboardFragment?.addLog("⚠️ Uninstall attempt blocked!", 0xFFFF6D00.toInt())
                reportsFragment?.addAlert("⚠️ Uninstall Attempt", "Someone tried to uninstall child monitor")
                showUrgentNotification("Uninstall Attempt!", "Someone tried to remove the child monitor app")
            }
            "grooming_alert" -> {
                val keyword = msg.optString("keyword")
                val pkg     = msg.optString("package")
                val preview = msg.optString("message_preview")
                handler.post {
                    reportsFragment?.addAlert("🚨 Grooming Keyword", "\"$keyword\" in ${pkg.substringAfterLast('.')}\n$preview")
                    dashboardFragment?.addLog("🚨 GROOMING: \"$keyword\" detected in ${pkg.substringAfterLast('.')}", 0xFFFF1744.toInt())
                    showUrgentNotification("Grooming Alert!", "\"$keyword\" detected in ${pkg.substringAfterLast('.')}")
                }
            }
            "call_blocked" -> {
                val num = msg.optString("number", "Unknown")
                handler.post {
                    dashboardFragment?.addLog("📵 Call blocked: $num")
                    reportsFragment?.addAlert("📵 Call Blocked", num)
                }
            }
            "app_data_cleared" -> {
                val pkg = msg.optString("package")
                val ok  = msg.optBoolean("success")
                handler.post { dashboardFragment?.addLog(if (ok) "✅ App data cleared: $pkg" else "❌ Clear failed: $pkg") }
            }
            "token_granted" -> {
                val pkg  = msg.optString("package")
                val mins = msg.optInt("minutes")
                handler.post { dashboardFragment?.addLog("🎮 Token granted: ${pkg.substringAfterLast('.')} for ${mins}min", 0xFF00C853.toInt()) }
            }
            "emergency_unlocked_all" -> {
                handler.post { dashboardFragment?.addLog("✅ Emergency lock released — all apps unblocked") }
            }
            "diagnostic_report" -> handler.post { dashboardFragment?.onDiagnosticReport(msg) }
            "diagnostic_cleared" -> {
                handler.post { dashboardFragment?.addLog("🗑 Diagnostic logs cleared on child device", 0xFFFF6D00.toInt()) }
            }
            "heartbeat" -> {
                val payload = msg.optJSONObject("payload") ?: msg
                handler.post { dashboardFragment?.onHeartbeat(payload) }
            }
            "wifi_changed" -> {
                val wok = msg.optBoolean("success", true)
                val wstate = msg.optString("state")
                handler.post { dashboardFragment?.addLog(if (wok) "Wi-Fi $wstate" else "WiFi control failed — enable Shizuku", if (wok) 0xFF00C853.toInt() else 0xFFFF5252.toInt()) }
            }
            "update_status" -> {
                val status  = msg.optString("status")
                val version = msg.optString("version", "")
                val error   = msg.optString("error", "")
                val (emoji, text) = when (status) {
                    "downloading"    -> "⬇️" to "Downloading update $version..."
                    "installed"      -> "✅" to "Updated to $version — child will restart"
                    "install_failed" -> "❌" to "Install failed for $version"
                    "download_failed"-> "❌" to "Download failed: $error"
                    else             -> "ℹ️" to "Update: $status"
                }
                handler.post { dashboardFragment?.addLog("$emoji $text", if (status == "installed") 0xFF00C853.toInt() else 0xFFFF5252.toInt()) }
            }
            "child_message" -> {
                val text = msg.optString("text","")
                val isDistress = msg.optBoolean("isDistress",false)
                val urgency = msg.optString("urgency","low")
                handler.post {
                    messagesFragment?.onChildMessage(msg)
                    // Bug 15: Always notify parent, not only on distress
                    if (isDistress || urgency == "high") {
                        showUrgentNotification("Child Message - urgent!", text.take(60))
                    } else {
                        showUrgentNotification("Child says:", text.take(60))
                    }
                    dashboardFragment?.addLog("Child msg: \"${text.take(40)}\"",
                        if (isDistress) 0xFFFF1744.toInt() else 0xFF7C4DFF.toInt())
                }
            }
            "danger_zone_alert" -> {
                val zoneName = msg.optString("zone_name", "Geofence")
                val inside   = msg.optBoolean("inside", false)
                val lat      = msg.optDouble("lat", 0.0)
                val lng      = msg.optDouble("lng", 0.0)
                val verb     = if (inside) "entered" else "exited"
                handler.post {
                    showUrgentNotification("Danger Zone $verb!", "Child $verb $zoneName")
                    dashboardFragment?.addLog("Geofence: $zoneName $verb (%.4f, %.4f)".format(lat, lng), 0xFFFF6D00.toInt())
                }
            }
            "health_status" -> {
                val payload = msg.optJSONObject("payload") ?: msg
                handler.post { dashboardFragment?.onHealthStatus(payload) }
            }
        }
    }

    private fun setConnected(on: Boolean) {
        connected = on
        if (on) reconnectDelay = 5_000L
        handler.post {
            tvStatus.text = if (on) "Online" else "Offline"
            tvStatus.setTextColor(if (on) 0xFF00C853.toInt() else 0xFFF44336.toInt())
            // Bug 6+17: Sync pill, dot, and topBarDot with connection state
            findViewById<android.view.View>(R.id.statusDot)?.setBackgroundResource(
                if (on) R.drawable.circle_green else R.drawable.circle_red)
            findViewById<android.view.View>(R.id.statusPill)?.setBackgroundResource(
                if (on) R.drawable.pill_online else R.drawable.pill_offline)
            findViewById<android.view.View>(R.id.topBarDot)?.setBackgroundResource(
                if (on) R.drawable.circle_green else R.drawable.circle_red)
            dashboardFragment?.updateOnline(on)
            if (on) startPinging() else stopPinging()
        }
    }

    private fun scheduleReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectCount++
        handler.post { dashboardFragment?.updateReconnectCount(reconnectCount) }
        reconnectRunnable = Runnable { if (!connected) { connect(); reconnectDelay = minOf(reconnectDelay * 2, 60_000L) } }
        handler.postDelayed(reconnectRunnable!!, reconnectDelay)
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

        // Keep Replit server awake — fires every 4 minutes
        keepaliveRunnable = object : Runnable {
            override fun run() {
                Thread {
                    try {
                        val url = java.net.URL(
                            "https://relay-server-production-bf46.up.railway.app")
                        val conn = url.openConnection()
                            as java.net.HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.responseCode
                        conn.disconnect()
                    } catch (_: Exception) {}
                }.start()
                handler.postDelayed(this, 240_000)
            }
        }
        handler.postDelayed(keepaliveRunnable!!, 240_000)
    }

    private fun stopPinging() {
        pingInterval?.let { handler.removeCallbacks(it) }
        keepaliveRunnable?.let { handler.removeCallbacks(it) }
    }

    fun sendToChild(data: JSONObject) {
        try { ws?.send(data.toString()) } catch (_: Exception) {}
    }

    fun sendCommand(command: String) {
        sendToChild(JSONObject().apply { put("command", command) })
    }

    fun sendCommandObj(data: JSONObject) {
        sendToChild(data)
    }

    private fun showUrgentNotification(title: String, body: String) {
        try {
            val mgr = getSystemService(android.app.NotificationManager::class.java)
            val channelId = "urgent"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val ch = android.app.NotificationChannel(channelId, "Urgent Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH)
                ch.enableVibration(true)
                mgr.createNotificationChannel(ch)
            }
            val notif = android.app.Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build()
            mgr.notify(System.currentTimeMillis().toInt(), notif)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopPinging()
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        ws?.close(1000, null)
        super.onDestroy()
    }
}
