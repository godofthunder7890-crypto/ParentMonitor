package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

class LocationFragment : Fragment(), OnMapReadyCallback {

    private var tvLat:  TextView? = null
    private var tvLng:  TextView? = null
    private var tvAcc:  TextView? = null
    private var tvHist: TextView? = null

    private var mapView:   MapView?    = null
    private var googleMap: GoogleMap?  = null
    private var marker:    Marker?     = null

    private val histLog = mutableListOf<String>()
    private var lastLat = 0.0
    private var lastLng = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity

        val scroll = ScrollView(ctx)
        val root   = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
        }
        scroll.addView(root)

        fun label(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFF888888.toInt()); textSize = 11f; setPadding(0, 8, 0, 2)
        }
        fun value() = TextView(ctx).apply {
            text = "--"; setTextColor(0xFF00E5FF.toInt()); textSize = 20f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        // ── Coordinate readouts ───────────────────────────────────────────────
        root.addView(label("LATITUDE"));  tvLat = value(); root.addView(tvLat)
        root.addView(label("LONGITUDE")); tvLng = value(); root.addView(tvLng)
        root.addView(label("ACCURACY"));  tvAcc = value(); root.addView(tvAcc)

        // ── Buttons ───────────────────────────────────────────────────────────
        val btnFetch = Button(ctx).apply {
            text = "📍 FETCH LOCATION"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF001A33.toInt())
            setTextColor(0xFF00E5FF.toInt())
        }
        btnFetch.setOnClickListener { act.wsManager.sendCommand("get_location") }
        root.addView(btnFetch)

        val btnCenter = Button(ctx).apply {
            text = "🗺 CENTER MAP"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF001A33.toInt())
            setTextColor(0xFF00E5FF.toInt())
        }
        btnCenter.setOnClickListener { centerMap() }
        root.addView(btnCenter)

        // ── Google Map ────────────────────────────────────────────────────────
        root.addView(label("LIVE MAP"))
        mapView = MapView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 900
            )
        }
        mapView!!.onCreate(savedInstanceState)
        mapView!!.getMapAsync(this)
        root.addView(mapView)

        // ── History log ───────────────────────────────────────────────────────
        root.addView(label("LOCATION HISTORY"))
        tvHist = TextView(ctx).apply {
            text = "No history"
            setTextColor(0xFF444466.toInt()); textSize = 11f; setPadding(0, 8, 0, 0)
        }
        root.addView(tvHist)

        (activity as? MainActivity)?.locationFragment = this
        return scroll
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isZoomControlsEnabled   = true
        map.uiSettings.isCompassEnabled        = true
        map.uiSettings.isMyLocationButtonEnabled = false

        // Dark map style
        try {
            map.setMapStyle(
                MapStyleOptions("""[{"featureType":"all","stylers":[{"invert_lightness":true},{"saturation":-30}]},
                {"featureType":"water","stylers":[{"color":"#00111a"}]},
                {"featureType":"road","stylers":[{"color":"#00e5ff","weight":0.5}]}]""")
            )
        } catch (_: Exception) {}

        // If we already have a location, place the marker
        if (lastLat != 0.0 || lastLng != 0.0) {
            placeMarker(lastLat, lastLng)
        }
    }

    fun onLocation(lat: Double, lng: Double, acc: Float = 0f) {
        lastLat = lat; lastLng = lng

        tvLat?.text = "%.6f".format(lat)
        tvLng?.text = "%.6f".format(lng)
        if (acc > 0) tvAcc?.text = "±${acc.toInt()}m"

        histLog.add(0, "%.5f, %.5f".format(lat, lng))
        if (histLog.size > 20) histLog.removeAt(histLog.size - 1)
        tvHist?.text = histLog.joinToString("\n")

        placeMarker(lat, lng)
    }

    private fun placeMarker(lat: Double, lng: Double) {
        val map = googleMap ?: return
        val pos = LatLng(lat, lng)
        if (marker == null) {
            marker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title("Child")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
        } else {
            marker!!.position = pos
        }
    }

    private fun centerMap() {
        if (lastLat == 0.0 && lastLng == 0.0) return
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lastLat, lastLng), 15f))
    }

    // ── MapView lifecycle forwarding ──────────────────────────────────────────
    override fun onResume()  { super.onResume();  mapView?.onResume()  }
    override fun onPause()   { super.onPause();   mapView?.onPause()   }
    override fun onStart()   { super.onStart();   mapView?.onStart()   }
    override fun onStop()    { super.onStop();    mapView?.onStop()    }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); mapView?.onSaveInstanceState(out) }

    override fun onDestroyView() {
        mapView?.onDestroy()
        (activity as? MainActivity)?.locationFragment = null
        super.onDestroyView()
    }
}
