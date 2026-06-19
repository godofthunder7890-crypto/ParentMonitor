package com.parent.monitor

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.json.JSONArray
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

    // Diagnostic views
    private var tvDiagBadge: TextView?     = null
    private var tvDiagCrashes: TextView?   = null
    private var tvDiagRestarts: TextView?  = null
    private var tvDiagAndroid: TextView?   = null
    private var tvDiagLastEvent: TextView? = null
    private var btnDiagFetch: Button?      = null
    private var btnDiagView: Button?       = null
    private var btnDiagClear: Button?      = null
    private var lastDiagReport: JSONObject? = null

    private val handler = Handler(Looper.getMainLooper())
    private var pulseAnim: AnimatorSet? = null
    private val activityLog = mutableListOf<Pair<String, Int>>()
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

        // Diagnostic buttons
        btnDiagFetch?.setOnClickListener {
            sendCmd("get_diagnostic_report")
            addLog("📋 Fetching diagnostic report...", 0xFF00C853.toInt())
            btnDiagFetch?.text = "📋 FETCHING..."
            btnDiagFetch?.isEnabled = false
            handler.postDelayed({ btnDiagFetch?.text = "📋 FETCH LOGS"; btnDiagFetch?.isEnabled = true }, 5000)
            popView(btnDiagFetch!!)
        }
        btnDiagView?.setOnClickListener {
            showDiagnosticDialog()
            popView(btnDiagView!!)
        }
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

        animateCardsIn(view)
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.dashboardFragment = null
        stopPulse()
        super.onDestroyView()
    }

    // ── Diagnostic report received from child device ───────────────────────────
    fun onDiagnosticReport(report: JSONObject) {
        if (!isAdded) return
        lastDiagReport = report

        val crashes  = report.optJSONArray("crashes")  ?: JSONArray()
        val restarts = report.optJSONArray("restarts") ?: JSONArray()
        val android  = report.optInt("android", 0)
        val device   = report.optString("device", "Unknown")

        val crashCount   = crashes.length()
        val watchdogRestarts = (0 until restarts.length()).count {
            restarts.getJSONObject(it).optString("type") == "WATCHDOG_RESTART"
        }

        tvDiagCrashes?.text  = crashCount.toString()
        tvDiagRestarts?.text = watchdogRestarts.toString()
        tvDiagAndroid?.text  = android.toString()

        // Badge color: red = crashes, yellow = restarts, green = healthy
        val badgeText: String
        val badgeColor: Int
        when {
            crashCount > 0 -> {
                badgeText  = "● $crashCount CRASH${if (crashCount > 1) "ES" else ""}"
                badgeColor = 0xFFFF5252.toInt()
            }
            watchdogRestarts > 0 -> {
                badgeText  = "● $watchdogRestarts RESTART${if (watchdogRestarts > 1) "S" else ""}"
                badgeColor = 0xFFFFD600.toInt()
            }
            else -> {
                badgeText  = "● HEALTHY"
                badgeColor = 0xFF00C853.toInt()
            }
        }
        tvDiagBadge?.text = badgeText
        tvDiagBadge?.setTextColor(badgeColor)
        tvDiagCrashes?.setTextColor(if (crashCount > 0) 0xFFFF5252.toInt() else 0xFF4CAF50.toInt())
        tvDiagRestarts?.setTextColor(if (watchdogRestarts > 0) 0xFFFFD600.toInt() else 0xFF4CAF50.toInt())

        // Last event line
        val lastEvent = when {
            crashes.length() > 0 -> {
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

        addLog("📋 Diagnostic: $crashCount crashes, $watchdogRestarts restarts | $device", badgeColor)

        // Animate the card in if first report
        view?.findViewById<View>(R.id.cardDiagnostic)?.let { card ->
            if (card.alpha == 0f) {
                card.animate().alpha(1f).setDuration(400).start()
            }
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
    }

    private fun showDiagnosticDialog() {
        val ctx = requireContext()
        val report = lastDiagReport

        if (report == null) {
            AlertDialog.Builder(ctx)
                .setTitle("📋 No Data Yet")
                .setMessage("Tap 'FETCH LOGS' first to pull diagnostic data from the child device.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val crashes  = report.optJSONArray("crashes")  ?: JSONArray()
        val restarts = report.optJSONArray("restarts") ?: JSONArray()
        val device   = report.optString("device", "Unknown")
        val android  = report.optInt("android", 0)

        val sb = StringBuilder()
        sb.appendLine("DEVICE: $device")
        sb.appendLine("ANDROID: $android")
        sb.appendLine("REPORT TIME: ${report.optString("report_time")}")
        sb.appendLine()

        sb.appendLine("══ CRASHES (${crashes.length()}) ══")
        if (crashes.length() == 0) {
            sb.appendLine("  ✅ No crashes recorded")
        } else {
            for (i in 0 until crashes.length()) {
                val c = crashes.getJSONObject(i)
                sb.appendLine()
                sb.appendLine("  [${c.optString("time")}]")
                sb.appendLine("  Exception: ${c.optString("exception").substringAfterLast('.')}")
                sb.appendLine("  Message: ${c.optString("message")}")
                sb.appendLine("  Thread: ${c.optString("thread")}")
                val stack = c.optString("stacktrace").lines().take(6).joinToString("\n  ")
                sb.appendLine("  Stack:\n  $stack")
            }
        }

        sb.appendLine()
        sb.appendLine("══ RESTARTS (${restarts.length()}) ══")
        if (restarts.length() == 0) {
            sb.appendLine("  ✅ No forced restarts")
        } else {
            for (i in 0 until restarts.length()) {
                val r = restarts.getJSONObject(i)
                sb.appendLine("  [${r.optString("time")}] ${r.optString("type")} — ${r.optString("message").ifEmpty { r.optString("reason") }}")
            }
        }

        val tv = TextView(ctx).apply {
            text = sb.toString()
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF00E5FF.toInt())
            setPadding(32, 24, 32, 24)
        }
        val sv = ScrollView(ctx).apply { addView(tv) }

        AlertDialog.Builder(ctx)
            .setTitle("📋 Diagnostic Report")
            .setView(sv)
            .setPositiveButton("Close", null)
            .setNeutralButton("Re-fetch") { _, _ ->
                sendCmd("get_diagnostic_report")
                addLog("📋 Re-fetching diagnostic report...", 0xFF00C853.toInt())
            }
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
        if (online) {
            addLog("✅ Child device connected", 0xFF00C853.toInt())
            startPulse()
        } else {
            addLog("❌ Child device disconnected", 0xFFFF1744.toInt())
            stopPulse()
        }
    }

    fun updateBattery(pct: Int, charging: Boolean) {
        if (!isAdded) return
        val symbol = if (charging) "⚡" else "%"
        tvDashBattery?.text = "$pct$symbol"
        val col = when {
            pct > 50 -> 0xFF00C853.toInt()
            pct > 20 -> 0xFFFFD600.toInt()
            else     -> 0xFFFF1744.toInt()
        }
        tvDashBattery?.setTextColor(col)
        popView(tvDashBattery!!)
        addLog("🔋 Battery: $pct%${if (charging) " ⚡" else ""}", col)
    }

    fun updatePing(ms: Long) {
        if (!isAdded) return
        tvDashPing?.text = "${ms}ms"
        val col = when {
            ms < 150 -> 0xFF00C853.toInt()
            ms < 400 -> 0xFFFFD600.toInt()
            else     -> 0xFFFF1744.toInt()
        }
        tvDashPing?.setTextColor(col)
        popView(tvDashPing!!)
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
        tvDashCurrentApp?.text = name
        popView(tvDashCurrentApp!!)
        addLog("📱 App: $name", 0xFFAA00FF.toInt())
    }

    fun addLog(msg: String, color: Int = 0xFF8888AA.toInt()) {
        if (!isAdded) return
        val line = "[${timeFmt.format(Date())}] $msg"
        activityLog.add(0, Pair(line, color))
        if (activityLog.size > 10) activityLog.removeAt(activityLog.lastIndex)
        renderLog()
    }

    private fun renderLog() {
        val ssb = SpannableStringBuilder()
        activityLog.forEachIndexed { idx, (line, color) ->
            val start = ssb.length
            ssb.append(line)
            val finalColor = if (idx == 0) color
                             else Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
            ssb.setSpan(ForegroundColorSpan(finalColor), start, ssb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (idx < activityLog.lastIndex) ssb.append("\n")
        }
        tvActivityLog?.text = ssb
        tvActivityLog?.animate()?.alpha(0f)?.setDuration(50)?.withEndAction {
            tvActivityLog?.text = ssb
            tvActivityLog?.animate()?.alpha(1f)?.setDuration(200)?.start()
        }?.start()
    }

    private fun startPulse() {
        stopPulse()
        val ring = statusRing ?: return
        ring.scaleX = 1f; ring.scaleY = 1f; ring.alpha = 0.7f
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.45f, 1f)
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.45f, 1f)
        val alpha  = ObjectAnimator.ofFloat(ring, "alpha", 0.7f, 0.1f, 0.7f)
        pulseAnim = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1400
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (pulseAnim != null) start()
                }
            })
            start()
        }
    }

    private fun stopPulse() {
        pulseAnim?.cancel(); pulseAnim = null
        statusRing?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(0.15f)?.setDuration(300)?.start()
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
            R.id.cardDiagnostic, R.id.cardLog
        )
        ids.forEachIndexed { i, id ->
            val card = root.findViewById<View>(id) ?: return@forEachIndexed
            card.translationY = 60f; card.alpha = 0f
            card.animate()
                .translationY(0f).alpha(1f)
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
