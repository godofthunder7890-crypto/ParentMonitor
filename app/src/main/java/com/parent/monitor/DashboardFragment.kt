package com.parent.monitor

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    // ── Basic status views ─────────────────────────────────────────────────────
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
    private var btnSOS: Button? = null
    private var sosActive = false

    // ── Service Health card views (15 indicators) ──────────────────────────────
    private var viewHeartbeatDot: View?       = null
    private var tvHeartbeatStatus: TextView?  = null
    private var tvHeartbeatAge: TextView?     = null
    private var tvHealthUptime: TextView?     = null
    private var tvConnQuality: TextView?      = null
    private var tvHealthCrashes: TextView?    = null
    private var tvHealthRestarts: TextView?   = null
    private var tvHealthAndroid: TextView?    = null
    private var tvStatusFgService: TextView?  = null
    private var tvStatusAccessibility: TextView? = null
    private var tvStatusNotifAccess: TextView?   = null
    private var tvStatusBatteryOpt: TextView?    = null
    private var tvHealthDevice: TextView?     = null
    private var tvHealthLastCrash: TextView?  = null
    private var tvHealthLastRestart: TextView? = null
    private var btnHealthRefresh: Button?     = null
    private var btnHealthExport: Button?      = null

    // ── Diagnostic views ───────────────────────────────────────────────────────
    private var tvDiagBadge: TextView?     = null
    private var tvDiagCrashes: TextView?   = null
    private var tvDiagRestarts: TextView?  = null
    private var tvDiagAndroid: TextView?   = null
    private var tvDiagLastEvent: TextView? = null
    private var btnDiagFetch: Button?      = null
    private var btnDiagView: Button?       = null
    private var btnDiagClear: Button?      = null
    private var lastDiagReport: JSONObject? = null

    // ── State ──────────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val healthHandler = Handler(Looper.getMainLooper())
    private var reconnectCount = 0

    private fun relayHttp(): String {
        val prefs = (activity as? MainActivity)
            ?.getSharedPreferences(MainActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val ws = prefs?.getString(MainActivity.KEY_SERVER_URL, RELAY_DEFAULT) ?: RELAY_DEFAULT
        return if (ws.startsWith("ws://")) ws.replace("ws://", "http://").removeSuffix("/api/ws")
               else ws.replace("wss://", "https://").removeSuffix("/api/ws")
    }

    fun updateReconnectCount(count: Int) {
        reconnectCount = count
        tvConnQuality?.let { tv ->
            val cur = tv.text.toString()
            // Append reconnect count to existing quality label
            val base = cur.substringBefore(" | WS:")
            tv.text = "$base | WS reconnects: $count"
        }
    }

    private fun fetchServerHealth() {
        Thread {
            try {
                val conn = java.net.URL("${relayHttp()}/health").openConnection()
                    as java.net.HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val js = org.json.JSONObject(body)
                val upSec = js.optLong("uptimeSeconds", js.optLong("uptime", 0L))
                val uptimeStr = when {
                    upSec > 86400 -> "${upSec / 86400}d ${(upSec % 86400) / 3600}h"
                    upSec > 3600  -> "${upSec / 3600}h ${(upSec % 3600) / 60}m"
                    else          -> "${upSec / 60}m ${upSec % 60}s"
                }
                val db     = js.optString("db", "ok")
                val status = js.optString("status", "ok")
                activity?.runOnUiThread {
                    addLog("Server: up=$uptimeStr  db=$db  reconnects=$reconnectCount", 0xFF00BBFF.toInt())
                    tvConnQuality?.text = "Server $status | up $uptimeStr | reconnects: $reconnectCount"
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    addLog("Server health check failed: ${e.message?.take(40)}", 0xFFFF5252.toInt())
                }
            }
        }.start()
    }
    private var pulseAnim: AnimatorSet? = null
    private var heartbeatPulse: AnimatorSet? = null
    private val activityLog = mutableListOf<Pair<String, Int>>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Real-time heartbeat tracking
    private var lastHeartbeatMs: Long = 0L
    private var lastHealthData: JSONObject? = null
    private val heartbeatTickerRunnable = object : Runnable {
        override fun run() {
            updateHeartbeatAge()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? =
        i.inflate(R.layout.fragment_dashboard, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Basic status views
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

        // Health card views
        viewHeartbeatDot        = view.findViewById(R.id.viewHeartbeatDot)
        tvHeartbeatStatus       = view.findViewById(R.id.tvHeartbeatStatus)
        tvHeartbeatAge          = view.findViewById(R.id.tvHeartbeatAge)
        tvHealthUptime          = view.findViewById(R.id.tvHealthUptime)
        tvConnQuality           = view.findViewById(R.id.tvConnQuality)
        tvHealthCrashes         = view.findViewById(R.id.tvHealthCrashes)
        tvHealthRestarts        = view.findViewById(R.id.tvHealthRestarts)
        tvHealthAndroid         = view.findViewById(R.id.tvHealthAndroid)
        tvStatusFgService       = view.findViewById(R.id.tvStatusFgService)
        tvStatusAccessibility   = view.findViewById(R.id.tvStatusAccessibility)
        tvStatusNotifAccess     = view.findViewById(R.id.tvStatusNotifAccess)
        tvStatusBatteryOpt      = view.findViewById(R.id.tvStatusBatteryOpt)
        tvHealthDevice          = view.findViewById(R.id.tvHealthDevice)
        tvHealthLastCrash       = view.findViewById(R.id.tvHealthLastCrash)
        tvHealthLastRestart     = view.findViewById(R.id.tvHealthLastRestart)
        btnHealthRefresh        = view.findViewById(R.id.btnHealthRefresh)
        btnHealthExport         = view.findViewById(R.id.btnHealthExport)

        // Diagnostic views
        tvDiagBadge     = view.findViewById(R.id.tvDiagBadge)
        tvDiagCrashes   = view.findViewById(R.id.tvDiagCrashes)
        tvDiagRestarts  = view.findViewById(R.id.tvDiagRestarts)
        tvDiagAndroid   = view.findViewById(R.id.tvDiagAndroid)
        tvDiagLastEvent = view.findViewById(R.id.tvDiagLastEvent)
        btnDiagFetch    = view.findViewById(R.id.btnDiagFetch)
        btnDiagView     = view.findViewById(R.id.btnDiagView)
        btnDiagClear    = view.findViewById(R.id.btnDiagClear)

        (activity as? MainActivity)?.dashboardFragment = this

        setupButtons()
        handler.postDelayed(heartbeatTickerRunnable, 1000)
        animateCardsIn(view)
    }

    private fun setupButtons() {
        btnDashLock?.setOnClickListener          { sendCmd("lock_screen"); popView(btnDashLock!!) }
        btnDashCamera?.setOnClickListener        { sendCmd("take_photo"); popView(btnDashCamera!!) }
        btnDashLocation?.setOnClickListener      { sendCmd("get_location"); popView(btnDashLocation!!) }
        btnDashBattery?.setOnClickListener       { sendCmd("get_battery"); popView(btnDashBattery!!) }
        btnDashCurrentApp?.setOnClickListener    { sendCmd("get_running_app"); popView(btnDashCurrentApp!!) }
        btnDashGrantPerms?.setOnClickListener    { sendCmd("grant_permissions"); popView(btnDashGrantPerms!!) }
        btnDashEmergencyLock?.setOnClickListener {
            sendCmd("emergency_lock")
            addLog("⚡ Emergency lock sent", 0xFFFF6D00.toInt())
            shakeView(btnDashEmergencyLock!!)
        }

        btnSOS = view?.findViewById<Button>(R.id.btnDashSOS)
        btnSOS?.setOnClickListener {
            if (!sosActive) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Send Emergency SOS?")
                    .setMessage("Turns on front camera, mic, location and alarm on child phone.")
                    .setPositiveButton("SEND SOS") { _, _ ->
                        sendCmd("emergency_sos")
                        addLog("🚨 SOS sent to child device!", 0xFFFF1744.toInt())
                        setSosActive(true)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Stop Emergency SOS?")
                    .setMessage("This will stop the camera, mic and alarm on the child phone.")
                    .setPositiveButton("STOP SOS") { _, _ ->
                        sendCmd("stop_sos")
                        addLog("🛑 SOS stop command sent", 0xFFFF6D00.toInt())
                        setSosActive(false)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Health card buttons
        btnHealthRefresh?.setOnClickListener {
            sendCmd("get_health_status")
            addLog("🔄 Refreshing service health...", 0xFF0099FF.toInt())
            popView(btnHealthRefresh!!)
            btnHealthRefresh?.isEnabled = false
            handler.postDelayed({ btnHealthRefresh?.isEnabled = true }, 3000)
        }
        btnHealthExport?.setOnClickListener {
            exportHealthReport()
            popView(btnHealthExport!!)
        }

        // Diagnostic buttons
        btnDiagFetch?.setOnClickListener {
            sendCmd("get_diagnostic_report")
            addLog("📋 Fetching diagnostic report...", 0xFF00C853.toInt())
            btnDiagFetch?.text = "📋 FETCHING..."
            btnDiagFetch?.isEnabled = false
            handler.postDelayed({ btnDiagFetch?.text = "📋 FETCH LOGS"; btnDiagFetch?.isEnabled = true }, 5000)
            popView(btnDiagFetch!!)
        }
        btnDiagView?.setOnClickListener  { showDiagnosticDialog(); popView(btnDiagView!!) }
        btnDiagClear?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Diagnostic Logs")
                .setMessage("This will erase all crash and restart logs from the child device. Continue?")
                .setPositiveButton("CLEAR") { _, _ ->
                    sendCmd("clear_diagnostic_logs")
                    lastDiagReport = null
                    resetDiagUI()
                    addLog("🗑 Diagnostic logs cleared", 0xFFFF6D00.toInt())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.dashboardFragment = null
        stopPulse()
        stopHeartbeatPulse()
        handler.removeCallbacks(heartbeatTickerRunnable)
        btnSOS = null
        super.onDestroyView()
    }

    // ── Heartbeat monitoring ───────────────────────────────────────────────────
    /**
     * Called every second to update "Xs ago" display.
     * If > 90s since last heartbeat → MISSED (red).
     * If < 90s → LIVE (green).
     */
    private fun updateHeartbeatAge() {
        if (!isAdded) return
        if (lastHeartbeatMs == 0L) {
            tvHeartbeatAge?.text    = "— waiting for signal —"
            tvHeartbeatAge?.setTextColor(0xFF445566.toInt())
            return
        }
        val ageSec = (System.currentTimeMillis() - lastHeartbeatMs) / 1000
        val ageText = when {
            ageSec < 60  -> "${ageSec}s ago"
            ageSec < 3600 -> "${ageSec / 60}m ${ageSec % 60}s ago"
            else         -> "${ageSec / 3600}h ${(ageSec % 3600) / 60}m ago"
        }
        val missed = ageSec > 90
        tvHeartbeatAge?.text = if (missed) "MISSED ⚠ ($ageText)" else ageText
        tvHeartbeatAge?.setTextColor(if (missed) 0xFFFF5252.toInt() else 0xFF00AAFF.toInt())

        if (missed) {
            tvHeartbeatStatus?.text = "MISSED"
            tvHeartbeatStatus?.setTextColor(0xFFFF5252.toInt())
            viewHeartbeatDot?.setBackgroundResource(R.drawable.circle_red)
            stopHeartbeatPulse()
        }
    }

    fun onHeartbeat(data: JSONObject) {
        if (!isAdded) return
        lastHeartbeatMs = System.currentTimeMillis()

        // Update heartbeat indicator
        tvHeartbeatStatus?.text = "LIVE"
        tvHeartbeatStatus?.setTextColor(0xFF00FF88.toInt())
        viewHeartbeatDot?.setBackgroundResource(R.drawable.circle_green)
        startHeartbeatPulse()

        // Update uptime from heartbeat
        val uptime = data.optString("uptime_formatted", "")
        if (uptime.isNotEmpty()) tvHealthUptime?.text = uptime

        // Update crash/restart from heartbeat
        val crashes  = data.optInt("crash_count", -1)
        val restarts = data.optInt("restart_count", -1)
        val quality  = data.optInt("connection_quality", -1)
        if (crashes >= 0)  updateCrashCounter(crashes)
        if (restarts >= 0) updateRestartCounter(restarts)
        if (quality >= 0)  updateConnQuality(quality)

        // Animate health card visible if first signal
        view?.findViewById<View>(R.id.cardHealth)?.let { card ->
            if (card.alpha < 0.5f) card.animate().alpha(1f).setDuration(500).start()
        }
    }

    fun onHealthStatus(data: JSONObject) {
        if (!isAdded) return
        lastHeartbeatMs = System.currentTimeMillis()
        lastHealthData  = data

        // 1. Uptime
        tvHealthUptime?.text = data.optString("uptime_formatted", "--")
        popView(tvHealthUptime!!)

        // 2. Connection quality
        updateConnQuality(data.optInt("connection_quality", 0))

        // 3. Crash count
        updateCrashCounter(data.optInt("crash_count", 0))

        // 4. Restart count
        updateRestartCounter(data.optInt("restart_count", 0))

        // 5. Android version
        val androidVer = data.optInt("android_version", 0)
        val androidRel = data.optString("android_release", "")
        tvHealthAndroid?.text = "$androidVer"
        tvHealthAndroid?.setTextColor(0xFF00E5FF.toInt())

        // 6. Battery optimization status
        val battExempt = data.optBoolean("battery_optimization_exempt", false)
        renderStatusRow(tvStatusBatteryOpt, "EXEMPT", battExempt)

        // 7. Accessibility status
        val accessEnabled = data.optBoolean("accessibility_enabled", false)
        renderStatusRow(tvStatusAccessibility, "ENABLED", accessEnabled)

        // 8. Notification access
        val notifAccess = data.optBoolean("notification_access", false)
        renderStatusRow(tvStatusNotifAccess, "GRANTED", notifAccess)

        // 9. Foreground service running
        val fgRunning = data.optBoolean("foreground_service_running", false)
        renderStatusRow(tvStatusFgService, "RUNNING", fgRunning)

        // 10. Heartbeat indicator
        tvHeartbeatStatus?.text = "LIVE"
        tvHeartbeatStatus?.setTextColor(0xFF00FF88.toInt())
        viewHeartbeatDot?.setBackgroundResource(R.drawable.circle_green)
        startHeartbeatPulse()

        // 11. Device manufacturer + model info
        val manufacturer = data.optString("manufacturer", "")
        val model        = data.optString("model", "")
        if (manufacturer.isNotEmpty()) {
            tvHealthDevice?.text = "📱 $manufacturer $model · Android $androidRel (API $androidVer)"
            tvHealthDevice?.setTextColor(0xFF336688.toInt())
        }

        // 12. Last crash reason
        val lastCrash    = data.optString("last_crash_reason", "None")
        val lastCrashAt  = data.optString("last_crash_time", "—")
        tvHealthLastCrash?.text = "Last crash: $lastCrash"
        tvHealthLastCrash?.setTextColor(
            if (lastCrash == "None") 0xFF335544.toInt() else 0xFF884444.toInt()
        )

        // 13. Last restart reason
        val lastRestart   = data.optString("last_restart_reason", "None")
        val lastRestartAt = data.optString("last_restart_time", "—")
        tvHealthLastRestart?.text = "Last restart: $lastRestart"
        tvHealthLastRestart?.setTextColor(
            if (lastRestart == "None") 0xFF334433.toInt() else 0xFF886633.toInt()
        )

        // Also sync into Diagnostic card
        tvDiagAndroid?.text = androidVer.toString()

        // Activity log summary
        val qualityLabel = qualityLabel(data.optInt("connection_quality", 0))
        addLog("💓 Health: ${data.optInt("crash_count")} crashes · ${data.optInt("restart_count")} restarts · $manufacturer · $qualityLabel", 0xFF00AAFF.toInt())

        // Show card if hidden
        view?.findViewById<View>(R.id.cardHealth)?.let { card ->
            if (card.alpha < 0.5f) card.animate().alpha(1f).setDuration(500).start()
        }
    }

    private fun renderStatusRow(tv: TextView?, label: String, ok: Boolean) {
        tv?.text = if (ok) "⬤ $label" else "⬤ NOT $label"
        tv?.setTextColor(if (ok) 0xFF00FF88.toInt() else 0xFFFF5252.toInt())
    }

    private fun updateCrashCounter(count: Int) {
        tvHealthCrashes?.text = count.toString()
        tvHealthCrashes?.setTextColor(if (count > 0) 0xFFFF5252.toInt() else 0xFF33AA55.toInt())
    }

    private fun updateRestartCounter(count: Int) {
        tvHealthRestarts?.text = count.toString()
        tvHealthRestarts?.setTextColor(if (count > 0) 0xFFFFD600.toInt() else 0xFF33AA55.toInt())
    }

    private fun updateConnQuality(quality: Int) {
        tvConnQuality?.text = "$quality"
        tvConnQuality?.setTextColor(when {
            quality >= 80 -> 0xFF00FF88.toInt()
            quality >= 50 -> 0xFFFFD600.toInt()
            else          -> 0xFFFF5252.toInt()
        })
    }

    private fun qualityLabel(q: Int) = when {
        q >= 80 -> "Excellent"
        q >= 50 -> "Fair"
        else    -> "Poor"
    }

    // ── Export diagnostic logs ────────────────────────────────────────────────
    private fun exportHealthReport() {
        val ctx = requireContext()
        val data = lastHealthData
        val report = lastDiagReport

        if (data == null && report == null) {
            AlertDialog.Builder(ctx)
                .setTitle("📤 No Health Data")
                .setMessage("Tap 'REFRESH' on the health card or 'FETCH LOGS' on diagnostics first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════")
        sb.appendLine("  GUARDIAN EYE — HEALTH EXPORT")
        sb.appendLine("  ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("═══════════════════════════════")

        if (data != null) {
            sb.appendLine()
            sb.appendLine("── SERVICE HEALTH ──")
            sb.appendLine("Device       : ${data.optString("manufacturer")} ${data.optString("model")}")
            sb.appendLine("Android      : ${data.optString("android_release")} (API ${data.optInt("android_version")})")
            sb.appendLine("Uptime       : ${data.optString("uptime_formatted")}")
            sb.appendLine("Conn Quality : ${data.optInt("connection_quality")}%  (${qualityLabel(data.optInt("connection_quality"))})")
            sb.appendLine("Crashes      : ${data.optInt("crash_count")}")
            sb.appendLine("Restarts     : ${data.optInt("restart_count")}")
            sb.appendLine("Last HB      : ${if (lastHeartbeatMs > 0) timeFmt.format(Date(lastHeartbeatMs)) else "—"}")
            sb.appendLine()
            sb.appendLine("── PERMISSIONS ──")
            sb.appendLine("Foreground Svc  : ${if (data.optBoolean("foreground_service_running")) "✅ Running" else "❌ Stopped"}")
            sb.appendLine("Accessibility   : ${if (data.optBoolean("accessibility_enabled")) "✅ Enabled" else "❌ Disabled"}")
            sb.appendLine("Notif Access    : ${if (data.optBoolean("notification_access")) "✅ Granted" else "❌ Denied"}")
            sb.appendLine("Battery Opt     : ${if (data.optBoolean("battery_optimization_exempt")) "✅ Exempt" else "❌ Not exempt"}")
            sb.appendLine()
            sb.appendLine("── LAST EVENTS ──")
            sb.appendLine("Last Crash   : ${data.optString("last_crash_reason")} @ ${data.optString("last_crash_time")}")
            sb.appendLine("Last Restart : ${data.optString("last_restart_reason")} @ ${data.optString("last_restart_time")}")
        }

        if (report != null) {
            val crashes  = report.optJSONArray("crashes")  ?: JSONArray()
            val restarts = report.optJSONArray("restarts") ?: JSONArray()
            sb.appendLine()
            sb.appendLine("── CRASH LOG (${crashes.length()}) ──")
            for (i in 0 until crashes.length()) {
                val c = crashes.getJSONObject(i)
                sb.appendLine("[${c.optString("time")}] ${c.optString("exception").substringAfterLast('.')}: ${c.optString("message").take(80)}")
                sb.appendLine("  Thread: ${c.optString("thread")}")
                sb.appendLine("  Stack: ${c.optString("stacktrace").lines().first()}")
            }
            sb.appendLine()
            sb.appendLine("── RESTART LOG (${restarts.length()}) ──")
            for (i in 0 until restarts.length()) {
                val r = restarts.getJSONObject(i)
                sb.appendLine("[${r.optString("time")}] ${r.optString("type")} — ${r.optString("message").ifEmpty { r.optString("reason") }}")
            }
        }

        sb.appendLine()
        sb.appendLine("═══════════════════════════════")

        val text = sb.toString()

        // Copy to clipboard
        try {
            val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("Health Report", text))
            Toast.makeText(ctx, "✅ Copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}

        // Show dialog with full text
        val tv = TextView(ctx).apply {
            this.text = text
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF00AAFF.toInt())
            setPadding(28, 20, 28, 20)
        }
        val sv = ScrollView(ctx).apply { addView(tv) }
        AlertDialog.Builder(ctx)
            .setTitle("📤 Exported Health Report")
            .setView(sv)
            .setPositiveButton("Done", null)
            .setNeutralButton("Copy Again") { _, _ ->
                try {
                    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Health Report", text))
                    Toast.makeText(ctx, "Copied!", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
            .show()

        addLog("📤 Health report exported (${text.length} bytes)", 0xFF00AAFF.toInt())
    }

    // ── Diagnostic report from child ───────────────────────────────────────────
    fun onDiagnosticReport(report: JSONObject) {
        if (!isAdded) return
        lastDiagReport = report

        val crashes  = report.optJSONArray("crashes")  ?: JSONArray()
        val restarts = report.optJSONArray("restarts") ?: JSONArray()
        val android  = report.optInt("android", 0)
        val device   = report.optString("device", "Unknown")

        val crashCount   = crashes.length()
        val watchdogRst  = (0 until restarts.length()).count {
            restarts.getJSONObject(it).optString("type") == "WATCHDOG_RESTART"
        }

        tvDiagCrashes?.text  = crashCount.toString()
        tvDiagRestarts?.text = watchdogRst.toString()
        tvDiagAndroid?.text  = android.toString()

        // Sync health counters too
        updateCrashCounter(crashCount)
        updateRestartCounter(watchdogRst)
        tvHealthAndroid?.text = android.toString()

        val badgeText: String; val badgeColor: Int
        when {
            crashCount > 0     -> { badgeText = "● $crashCount CRASH${if (crashCount > 1) "ES" else ""}"; badgeColor = 0xFFFF5252.toInt() }
            watchdogRst > 0    -> { badgeText = "● $watchdogRst RESTART${if (watchdogRst > 1) "S" else ""}"; badgeColor = 0xFFFFD600.toInt() }
            else               -> { badgeText = "● HEALTHY"; badgeColor = 0xFF00C853.toInt() }
        }
        tvDiagBadge?.text = badgeText
        tvDiagBadge?.setTextColor(badgeColor)
        tvDiagCrashes?.setTextColor(if (crashCount > 0) 0xFFFF5252.toInt() else 0xFF4CAF50.toInt())
        tvDiagRestarts?.setTextColor(if (watchdogRst > 0) 0xFFFFD600.toInt() else 0xFF4CAF50.toInt())

        val lastEvent = when {
            crashes.length() > 0  -> {
                val last = crashes.getJSONObject(crashes.length() - 1)
                "Last crash: ${last.optString("time")} — ${last.optString("exception").substringAfterLast('.')}"
            }
            restarts.length() > 0 -> {
                val last = restarts.getJSONObject(restarts.length() - 1)
                "Last event: ${last.optString("time")} — ${last.optString("type")}"
            }
            else -> "$device · Android $android · All clear"
        }
        tvDiagLastEvent?.text = lastEvent

        addLog("📋 Diagnostic: $crashCount crashes, $watchdogRst restarts | $device", badgeColor)

        view?.findViewById<View>(R.id.cardDiagnostic)?.let { card ->
            if (card.alpha == 0f) card.animate().alpha(1f).setDuration(400).start()
        }
    }

    private fun resetDiagUI() {
        tvDiagCrashes?.text  = "--"
        tvDiagRestarts?.text = "--"
        tvDiagAndroid?.text  = "--"
        tvDiagBadge?.text    = "● HEALTHY"
        tvDiagBadge?.setTextColor(0xFF00C853.toInt())
        tvDiagCrashes?.setTextColor(0xFF4CAF50.toInt())
        tvDiagRestarts?.setTextColor(0xFF4CAF50.toInt())
        tvDiagLastEvent?.text = "— Logs cleared —"
        tvHealthCrashes?.text = "0"
        tvHealthRestarts?.text = "0"
        tvHealthLastCrash?.text  = "Last crash: —"
        tvHealthLastRestart?.text = "Last restart: —"
    }

    private fun showDiagnosticDialog() {
        val ctx = requireContext()
        val report = lastDiagReport

        if (report == null) {
            AlertDialog.Builder(ctx).setTitle("📋 No Data Yet")
                .setMessage("Tap 'FETCH LOGS' first to pull diagnostic data from the child device.")
                .setPositiveButton("OK", null).show()
            return
        }

        val crashes  = report.optJSONArray("crashes")  ?: JSONArray()
        val restarts = report.optJSONArray("restarts") ?: JSONArray()
        val sb = StringBuilder()
        sb.appendLine("DEVICE: ${report.optString("device")}")
        sb.appendLine("ANDROID: ${report.optInt("android")}")
        sb.appendLine("REPORT TIME: ${report.optString("report_time")}")
        sb.appendLine()
        sb.appendLine("══ CRASHES (${crashes.length()}) ══")
        if (crashes.length() == 0) sb.appendLine("  ✅ No crashes recorded")
        else for (i in 0 until crashes.length()) {
            val c = crashes.getJSONObject(i)
            sb.appendLine()
            sb.appendLine("  [${c.optString("time")}]")
            sb.appendLine("  Exception: ${c.optString("exception").substringAfterLast('.')}")
            sb.appendLine("  Message: ${c.optString("message")}")
            sb.appendLine("  Thread: ${c.optString("thread")}")
            val stack = c.optString("stacktrace").lines().take(6).joinToString("\n  ")
            sb.appendLine("  Stack:\n  $stack")
        }
        sb.appendLine()
        sb.appendLine("══ RESTARTS (${restarts.length()}) ══")
        if (restarts.length() == 0) sb.appendLine("  ✅ No forced restarts")
        else for (i in 0 until restarts.length()) {
            val r = restarts.getJSONObject(i)
            sb.appendLine("  [${r.optString("time")}] ${r.optString("type")} — ${r.optString("message").ifEmpty { r.optString("reason") }}")
        }

        val tv = TextView(ctx).apply {
            text = sb.toString(); textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF00E5FF.toInt()); setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(ctx).setTitle("📋 Diagnostic Report")
            .setView(ScrollView(ctx).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Re-fetch") { _, _ -> sendCmd("get_diagnostic_report") }
            .show()
    }

    // ── Standard fragment methods ──────────────────────────────────────────────
    fun updateOnline(online: Boolean) {
        if (!isAdded) return
        val color = if (online) 0xFF00C853.toInt() else 0xFFFF1744.toInt()
        tvChildStatus?.text = if (online) "ONLINE" else "OFFLINE"
        animateTextColor(tvChildStatus, if (online) 0xFFFF1744.toInt() else 0xFF00C853.toInt(), color)
        animateDotColor(bigStatusDot, color)
        tvLastSeen?.text = if (online) "Connected  ${timeFmt.format(Date())}" else "Last seen  ${timeFmt.format(Date())}"
        if (online) { addLog("✅ Child device connected", 0xFF00C853.toInt()); startPulse() }
        else        { addLog("❌ Child device disconnected", 0xFFFF1744.toInt()); stopPulse() }
    }

    fun updateBattery(pct: Int, charging: Boolean) {
        if (!isAdded) return
        val symbol = if (charging) "⚡" else "%"
        tvDashBattery?.text = "$pct$symbol"
        val col = when { pct > 50 -> 0xFF00C853.toInt(); pct > 20 -> 0xFFFFD600.toInt(); else -> 0xFFFF1744.toInt() }
        tvDashBattery?.setTextColor(col); popView(tvDashBattery!!)
        addLog("🔋 Battery: $pct%${if (charging) " ⚡" else ""}", col)
    }

    fun updatePing(ms: Long) {
        if (!isAdded) return
        tvDashPing?.text = "${ms}ms"
        val col = when { ms < 150 -> 0xFF00C853.toInt(); ms < 400 -> 0xFFFFD600.toInt(); else -> 0xFFFF1744.toInt() }
        tvDashPing?.setTextColor(col); popView(tvDashPing!!)
    }

    fun updateLocation(lat: Double, lng: Double) {
        if (!isAdded) return
        tvDashLocation?.text = "%.4f\n%.4f".format(lat, lng)
        popView(tvDashLocation!!)
        addLog("📍 Location updated", 0xFFFFD600.toInt())
    }

    fun updateCurrentApp(pkg: String) {
        if (!isAdded) return
        val name = pkg.substringAfterLast('.')
        tvDashCurrentApp?.text = name; popView(tvDashCurrentApp!!)
        addLog("📱 App: $name", 0xFFAA00FF.toInt())
    }

    fun addLog(msg: String, color: Int = 0xFF8888AA.toInt()) {
        if (!isAdded) return
        activityLog.add(0, Pair("[${timeFmt.format(Date())}] $msg", color))
        if (activityLog.size > 12) activityLog.removeAt(activityLog.lastIndex)
        renderLog()
    }

    fun setSosActive(active: Boolean) {
        sosActive = active
        if (!isAdded) return
        if (active) {
            btnSOS?.text = "🛑 STOP SOS"
            btnSOS?.setBackgroundColor(0xFFFF6D00.toInt())
        } else {
            btnSOS?.text = "🚨 EMERGENCY SOS"
            btnSOS?.setBackgroundColor(0xFFFF1744.toInt())
        }
    }

    private fun renderLog() {
        val ssb = SpannableStringBuilder()
        activityLog.forEachIndexed { idx, (line, color) ->
            val start = ssb.length; ssb.append(line)
            val finalColor = if (idx == 0) color
                             else Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
            ssb.setSpan(ForegroundColorSpan(finalColor), start, ssb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (idx < activityLog.lastIndex) ssb.append("\n")
        }
        tvActivityLog?.animate()?.alpha(0f)?.setDuration(50)?.withEndAction {
            tvActivityLog?.text = ssb
            tvActivityLog?.animate()?.alpha(1f)?.setDuration(200)?.start()
        }?.start()
    }

    // ── Animations ─────────────────────────────────────────────────────────────
    private fun startPulse() {
        stopPulse()
        val ring = statusRing ?: return
        ring.scaleX = 1f; ring.scaleY = 1f; ring.alpha = 0.7f
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.45f, 1f)
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.45f, 1f)
        val alpha  = ObjectAnimator.ofFloat(ring, "alpha", 0.7f, 0.1f, 0.7f)
        pulseAnim = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha); duration = 1400
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (pulseAnim != null) start() }
            })
            start()
        }
    }

    private fun stopPulse() {
        pulseAnim?.cancel(); pulseAnim = null
        statusRing?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(0.15f)?.setDuration(300)?.start()
    }

    private fun startHeartbeatPulse() {
        stopHeartbeatPulse()
        val dot = viewHeartbeatDot ?: return
        heartbeatPulse = AnimatorSet().apply {
            val sx = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.6f, 1f)
            val sy = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.6f, 1f)
            val al = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.4f, 1f)
            playTogether(sx, sy, al); duration = 900
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { if (heartbeatPulse != null) start() }
            })
            start()
        }
    }

    private fun stopHeartbeatPulse() {
        heartbeatPulse?.cancel(); heartbeatPulse = null
        viewHeartbeatDot?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(0.6f)?.setDuration(300)?.start()
    }

    private fun popView(v: View) {
        v.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90)
            .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(OvershootInterpolator(3f)).start() }.start()
    }

    private fun shakeView(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, -18f, 18f, -14f, 14f, -8f, 8f, 0f)
            .apply { duration = 400; start() }
    }

    private fun animateTextColor(tv: TextView?, from: Int, to: Int) {
        val anim = ValueAnimator.ofArgb(from, to)
        anim.duration = 350
        anim.addUpdateListener { tv?.setTextColor(it.animatedValue as Int) }
        anim.start()
    }

    private fun animateDotColor(v: View?, to: Int) {
        v?.animate()?.scaleX(0.7f)?.scaleY(0.7f)?.setDuration(120)?.withEndAction {
            v.setBackgroundColor(to)
            v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator(4f)).start()
        }?.start()
    }

    private fun animateCardsIn(root: View) {
        val ids = intArrayOf(
            R.id.cardStatus, R.id.cardBattery, R.id.cardPing,
            R.id.cardApp, R.id.cardLocation, R.id.cardActions,
            R.id.cardHealth, R.id.cardDiagnostic, R.id.cardLog
        )
        ids.forEachIndexed { i, id ->
            val card = root.findViewById<View>(id) ?: return@forEachIndexed
            card.translationY = 60f; card.alpha = 0f
            card.animate().translationY(0f).alpha(1f)
                .setStartDelay((i * 60).toLong())
                .setDuration(380)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun sendCmd(command: String) {
        (activity as? MainActivity)?.sendCommand(command)
    }
}
