package com.parent.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class ControlFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(
            R.layout.fragment_control, container, false)

        val activity = requireActivity() as MainActivity

        view.findViewById<Button>(R.id.btnLock).setOnClickListener {
            activity.wsManager?.sendCommand("lock_screen")
        }

        view.findViewById<Button>(R.id.btnLocation).setOnClickListener {
            activity.wsManager?.sendCommand("get_location")
        }

        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            activity.wsManager?.sendCommand("take_photo")
        }

        view.findViewById<Button>(R.id.btnBattery).setOnClickListener {
            activity.wsManager?.sendCommand("get_battery")
        }

        return view
    }

    fun updateBattery(level: Int) {
        view?.findViewById<TextView>(R.id.tvBatteryDetail)
            ?.text = "🔋 Battery: $level%"
    }

    fun updateLocation(lat: Double, lng: Double) {
        view?.findViewById<TextView>(R.id.tvLocationInfo)
            ?.text = "📍 Lat: $lat\nLng: $lng"
    }
}
