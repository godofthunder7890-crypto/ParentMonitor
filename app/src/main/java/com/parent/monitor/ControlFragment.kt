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
    private var lastTouchX    = 0f
    private var lastTouchY    = 0f
    private var lastSwipeSent = 0L  // BUG #10 FIX: throttle swipe flood
    private var targetW = 1080f  // Bug #22 fix: updated dynamically from child screen_width
    private var targetH = 1920f

    fun onDeviceInfo(screenWidth: Int, screenHeight: Int) {
        if (screenWidth > 0 && screenHeight > 0) { targetW = screenWidth.toFloat(); targetH = screenHeight.toFloat() }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_control, c, false)
        tvTouchCoords = v.findViewById(R.id.tvTouchCoords)
        setupTouchpad(v)
        setupButtons(v)
        // BUG #11 FIX: Apply stored device dims if Controls tab opened after device_info arrived
        (activity as? MainActivity)?.let { act ->
            if (act.lastDeviceW > 0 && act.lastDeviceH > 0) onDeviceInfo(act.lastDeviceW, act.lastDeviceH)
        }
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
                    if (Math.abs(rx - lastTouchX) > 5 || Math.abs(ry - lastTouchY) > 5) {
                        act.sendCommandObj(JSONObject().apply {
                            put("command","swipe"); put("x1",lastTouchX); put("y1",lastTouchY)
                            put("x2",rx); put("y2",ry); put("duration",80)
                        })
                        lastTouchX = rx; lastTouchY = ry
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (Math.abs(rx - lastTouchX) < 20 && Math.abs(ry - lastTouchY) < 20) {
                        act.sendCommandObj(JSONObject().apply {
                            put("command","touch"); put("x",rx); put("y",ry)
                        })
                    }
                }
            }
            true
        }
    }

    private fun setupButtons(v: View) {
        val act = requireActivity() as MainActivity
        v.findViewById<Button>(R.id.btnNavBack).setOnClickListener    { act.sendCommand("key_back") }
        v.findViewById<Button>(R.id.btnNavHome).setOnClickListener    { act.sendCommand("key_home") }
        v.findViewById<Button>(R.id.btnNavRecents).setOnClickListener { act.sendCommand("key_recents") }
        v.findViewById<Button>(R.id.btnCtrlLock).setOnClickListener   { act.sendCommand("lock_screen") }
        v.findViewById<Button>(R.id.btnCtrlLocation).setOnClickListener { act.sendCommand("get_location") }
        v.findViewById<Button>(R.id.btnCtrlBattery).setOnClickListener  { act.sendCommand("get_battery") }

        v.findViewById<Button>(R.id.btnFindKid).setOnClickListener {
            act.sendCommandObj(JSONObject().apply { put("command","find_kid"); put("duration_seconds", 30) })
            android.widget.Toast.makeText(requireContext(), "📳 Ringing child's phone for 30s...", android.widget.Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button>(R.id.btnStopRing).setOnClickListener {
            act.sendCommand("stop_find_kid")
            android.widget.Toast.makeText(requireContext(), "🔕 Ring stopped", android.widget.Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button>(R.id.btnSnapNowCtrl).setOnClickListener {
            act.sendCommand("snapshot_now")
            android.widget.Toast.makeText(requireContext(), "📸 Capturing screenshot...", android.widget.Toast.LENGTH_SHORT).show()
        }
        v.findViewById<Button>(R.id.btnEmergencyLockAll).setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("🚨 Emergency Lock All Apps?")
                .setMessage("This will block ALL apps on the child's phone except the home screen. Child will not be able to open any app.")
                .setPositiveButton("LOCK ALL") { _, _ ->
                    act.sendCommand("emergency_lock_all")
                    android.widget.Toast.makeText(requireContext(), "Emergency lock sent!", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        v.findViewById<Button>(R.id.btnEmergencyUnlock).setOnClickListener {
            act.sendCommand("emergency_unlock_all")
            android.widget.Toast.makeText(requireContext(), "Unlock command sent!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
