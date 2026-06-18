package com.parent.monitor

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject

class TrackFragment : Fragment() {

    private var tvTrackLat: TextView? = null
    private var tvTrackLng: TextView? = null
    private var tvTrackAccuracy: TextView? = null
    private var tvTrackStatus: TextView? = null
    private var tvGeofenceStatus: TextView? = null
    private var tvSosStatus: TextView? = null
    private var lvLocationHistory: ListView? = null
    private var sosIndicator: View? = null
    private val locationHistory = mutableListOf<String>()
    private var histAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_track, c, false)
        val act = requireActivity() as MainActivity

        tvTrackLat       = v.findViewById(R.id.tvTrackLat)
        tvTrackLng       = v.findViewById(R.id.tvTrackLng)
        tvTrackAccuracy  = v.findViewById(R.id.tvTrackAccuracy)
        tvTrackStatus    = v.findViewById(R.id.tvTrackStatus)
        tvGeofenceStatus = v.findViewById(R.id.tvGeofenceStatus)
        tvSosStatus      = v.findViewById(R.id.tvSosStatus)
        sosIndicator     = v.findViewById(R.id.sosIndicator)
        lvLocationHistory= v.findViewById(R.id.lvLocationHistory)

        histAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, locationHistory)
        lvLocationHistory?.adapter = histAdapter

        // Fetch location
        v.findViewById<Button>(R.id.btnGetLocation).setOnClickListener {
            act.wsManager?.sendCommand("get_location")
            tvTrackStatus?.text = "📡 Fetching location..."
        }

        // Open in Maps
        v.findViewById<Button>(R.id.btnOpenMaps).setOnClickListener {
            val lat = tvTrackLat?.text?.toString()?.toDoubleOrNull() ?: return@setOnClickListener
            val lng = tvTrackLng?.text?.toString()?.toDoubleOrNull() ?: return@setOnClickListener
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(Child Location)"))
                startActivity(intent)
            } catch (_: Exception) {}
        }

        // Geofence setup
        val etGeoLat    = v.findViewById<EditText>(R.id.etGeoLat)
        val etGeoLng    = v.findViewById<EditText>(R.id.etGeoLng)
        val etGeoRadius = v.findViewById<EditText>(R.id.etGeoRadius)

        v.findViewById<Button>(R.id.btnUseCurrentAsGeo).setOnClickListener {
            etGeoLat.setText(tvTrackLat?.text)
            etGeoLng.setText(tvTrackLng?.text)
            tvGeofenceStatus?.text = "Current location loaded — tap Set Geofence"
        }

        v.findViewById<Button>(R.id.btnSetGeofence).setOnClickListener {
            val lat    = etGeoLat.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val lng    = etGeoLng.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val radius = etGeoRadius.text.toString().toFloatOrNull() ?: 200f
            act.wsManager?.sendCommandObj(JSONObject().apply {
                put("command", "set_geofence"); put("lat", lat)
                put("lng", lng); put("radius", radius)
            })
            tvGeofenceStatus?.text = "✅ Geofence set: ${radius}m radius"
            tvGeofenceStatus?.setTextColor(0xFF4CAF50.toInt())
        }

        v.findViewById<Button>(R.id.btnDisableGeofence).setOnClickListener {
            act.wsManager?.sendCommand("disable_geofence")
            tvGeofenceStatus?.text = "Geofence disabled"
            tvGeofenceStatus?.setTextColor(0xFF555555.toInt())
        }

        // Location history clear
        v.findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            locationHistory.clear(); histAdapter?.notifyDataSetChanged()
        }

        return v
    }

    fun onLocationUpdate(lat: Double, lng: Double, accuracy: Float, geofenceInside: Boolean?) {
        tvTrackLat?.text  = "%.6f".format(lat)
        tvTrackLng?.text  = "%.6f".format(lng)
        tvTrackAccuracy?.text = "±${accuracy.toInt()}m"
        tvTrackStatus?.text = "📍 Updated"
        tvTrackStatus?.setTextColor(0xFF4CAF50.toInt())

        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "$time  |  ${"%.5f".format(lat)}, ${"%.5f".format(lng)}"
        locationHistory.add(0, entry)
        if (locationHistory.size > 100) locationHistory.removeAt(locationHistory.size - 1)
        histAdapter?.notifyDataSetChanged()

        geofenceInside?.let { inside ->
            tvGeofenceStatus?.text = if (inside) "🟢 Child inside safe zone" else "🔴 Child OUTSIDE safe zone!"
            tvGeofenceStatus?.setTextColor(if (inside) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        }
    }

    fun onGeofenceAlert(inside: Boolean, distM: Int) {
        tvGeofenceStatus?.text = if (inside)
            "🟢 Returned to safe zone (${distM}m from center)"
        else
            "🚨 ALERT: Child left safe zone! ${distM}m away"
        tvGeofenceStatus?.setTextColor(if (inside) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
    }

    fun onSosAlert() {
        tvSosStatus?.text = "🚨 SOS! CHILD TRIGGERED PANIC BUTTON!"
        tvSosStatus?.setTextColor(0xFFFF0000.toInt())
        // Pulse the SOS indicator
        sosIndicator?.let { dot ->
            dot.setBackgroundResource(R.drawable.circle_red)
            ObjectAnimator.ofFloat(dot, "scaleX", 1f, 2f, 1f).apply {
                duration = 400; repeatCount = ValueAnimator.INFINITE; start() }
            ObjectAnimator.ofFloat(dot, "scaleY", 1f, 2f, 1f).apply {
                duration = 400; repeatCount = ValueAnimator.INFINITE; start() }
        }
    }
}
