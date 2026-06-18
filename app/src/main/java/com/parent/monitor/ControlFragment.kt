package com.parent.monitor

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

class ControlFragment : Fragment() {

    private var tvTouchCoords: TextView? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Assume child screen is 1080x1920; coords will be normalised by touchpad size
    private val targetW = 1080f
    private val targetH = 1920f

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_control, c, false)
        tvTouchCoords = v.findViewById(R.id.tvTouchCoords)
        setupTouchpad(v)
        setupButtons(v)
        return v
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchpad(v: View) {
        val pad = v.findViewById<View>(R.id.touchPad)
        val act = requireActivity() as MainActivity

        pad.setOnTouchListener { view, event ->
            val rx = (event.x / view.width  * targetW).coerceIn(0f, targetW)
            val ry = (event.y / view.height * targetH).coerceIn(0f, targetH)
            tvTouchCoords?.text = "x:${rx.toInt()} y:${ry.toInt()}"

            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = rx; lastTouchY = ry }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(rx - lastTouchX)
                    val dy = Math.abs(ry - lastTouchY)
                    if (dx > 5 || dy > 5) {
                        act.wsManager?.sendCommandObj(JSONObject().apply {
                            put("command", "swipe")
                            put("x1", lastTouchX); put("y1", lastTouchY)
                            put("x2", rx);         put("y2", ry)
                            put("duration", 80)
                        })
                        lastTouchX = rx; lastTouchY = ry
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // If barely moved → it's a tap
                    val dx = Math.abs(rx - lastTouchX)
                    val dy = Math.abs(ry - lastTouchY)
                    if (dx < 20 && dy < 20) {
                        act.wsManager?.sendCommandObj(JSONObject().apply {
                            put("command", "touch")
                            put("x", rx); put("y", ry)
                        })
                    }
                }
            }
            true
        }
    }

    private fun setupButtons(v: View) {
        val act = requireActivity() as MainActivity
        v.findViewById<Button>(R.id.btnNavBack).setOnClickListener    { act.wsManager?.sendCommand("key_back") }
        v.findViewById<Button>(R.id.btnNavHome).setOnClickListener    { act.wsManager?.sendCommand("key_home") }
        v.findViewById<Button>(R.id.btnNavRecents).setOnClickListener { act.wsManager?.sendCommand("key_recents") }
        v.findViewById<Button>(R.id.btnCtrlLock).setOnClickListener   { act.wsManager?.sendCommand("lock_screen") }
        v.findViewById<Button>(R.id.btnCtrlLocation).setOnClickListener { act.wsManager?.sendCommand("get_location") }
        v.findViewById<Button>(R.id.btnCtrlBattery).setOnClickListener { act.wsManager?.sendCommand("get_battery") }
    }

    fun updateBattery(level: Int) { /* shown in dashboard */ }
    fun updateLocation(lat: Double, lng: Double) { /* shown in dashboard */ }
}
