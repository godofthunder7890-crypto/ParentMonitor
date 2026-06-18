package com.parent.monitor

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var tvChildStatus: TextView? = null
    private var tvLastSeen: TextView? = null
    private var bigStatusDot: View? = null
    private var statusRing: View? = null
    private var tvDashBattery: TextView? = null
    private var tvDashPing: TextView? = null
    private var tvDashCurrentApp: TextView? = null
    private var tvDashLocation: TextView? = null
    private var pulseAnimX: ObjectAnimator? = null
    private var pulseAnimY: ObjectAnimator? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_dashboard, c, false)
        tvChildStatus    = v.findViewById(R.id.tvChildStatus)
        tvLastSeen       = v.findViewById(R.id.tvLastSeen)
        bigStatusDot     = v.findViewById(R.id.bigStatusDot)
        statusRing       = v.findViewById(R.id.statusRing)
        tvDashBattery    = v.findViewById(R.id.tvDashBattery)
        tvDashPing       = v.findViewById(R.id.tvDashPing)
        tvDashCurrentApp = v.findViewById(R.id.tvDashCurrentApp)
        tvDashLocation   = v.findViewById(R.id.tvDashLocation)

        val act = requireActivity() as MainActivity
        v.findViewById<Button>(R.id.btnDashLock).setOnClickListener {
            animateButton(it); act.wsManager?.sendCommand("lock_screen") }
        v.findViewById<Button>(R.id.btnDashBattery).setOnClickListener {
            animateButton(it); act.wsManager?.sendCommand("get_battery") }
        v.findViewById<Button>(R.id.btnDashLocation).setOnClickListener {
            animateButton(it); act.wsManager?.sendCommand("get_location") }
        v.findViewById<Button>(R.id.btnDashCamera).setOnClickListener {
            animateButton(it); act.wsManager?.sendCommand("take_photo") }
        v.findViewById<Button>(R.id.btnDashCurrentApp).setOnClickListener {
            animateButton(it); act.wsManager?.sendCommand("get_running_app") }
        v.findViewById<Button>(R.id.btnDashGrantPerms).setOnClickListener {
            animateButton(it); act.wsManager?.sendCommand("grant_permissions") }
        return v
    }

    fun setChildOnline() {
        tvChildStatus?.text = "● CHILD ONLINE"
        tvChildStatus?.setTextColor(0xFF4CAF50.toInt())
        bigStatusDot?.setBackgroundResource(R.drawable.circle_green)
        tvLastSeen?.text = "Last seen: " +
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        startPulse()
    }

    fun setChildOffline() {
        tvChildStatus?.text = "● CHILD OFFLINE"
        tvChildStatus?.setTextColor(0xFFF44336.toInt())
        bigStatusDot?.setBackgroundResource(R.drawable.circle_red)
        statusRing?.setBackgroundResource(R.drawable.circle_red)
        stopPulse()
    }

    fun updateBattery(level: Int) {
        tvDashBattery?.text = "$level%"
        tvDashBattery?.setTextColor(when {
            level < 20 -> 0xFFF44336.toInt()
            level < 50 -> 0xFFFFB300.toInt()
            else       -> 0xFF00E676.toInt()
        })
        // Animate the value change
        tvDashBattery?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(120)
            ?.withEndAction { tvDashBattery?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start() }
            ?.start()
    }

    fun updatePing(ms: Long) {
        tvDashPing?.text = "${ms}ms"
        tvDashPing?.setTextColor(when {
            ms > 500 -> 0xFFF44336.toInt()
            ms > 200 -> 0xFFFFB300.toInt()
            else     -> 0xFF00E5FF.toInt()
        })
    }

    fun updateCurrentApp(pkg: String) {
        val name = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }.take(14)
        tvDashCurrentApp?.text = name
        tvDashCurrentApp?.animate()?.alpha(0f)?.setDuration(80)
            ?.withEndAction {
                tvDashCurrentApp?.text = name
                tvDashCurrentApp?.animate()?.alpha(1f)?.setDuration(150)?.start()
            }?.start()
    }

    fun updateLocation(lat: Double, lng: Double) {
        val text = "${"%.5f".format(lat)}, ${"%.5f".format(lng)}"
        tvDashLocation?.text = text
        tvDashLocation?.setTextColor(0xFF00E5FF.toInt())
    }

    private fun startPulse() {
        val ring = statusRing ?: return
        stopPulse()
        ring.setBackgroundResource(R.drawable.circle_green)
        pulseAnimX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 2.2f, 1f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(); start()
        }
        pulseAnimY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 2.2f, 1f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(); start()
        }
        ValueAnimator.ofFloat(1f, 0f, 0.4f, 0f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { ring.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimX?.cancel(); pulseAnimY?.cancel()
        statusRing?.scaleX = 1f; statusRing?.scaleY = 1f
        statusRing?.alpha = 0.2f
    }

    private fun animateButton(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80)
            .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPulse()
    }
}
