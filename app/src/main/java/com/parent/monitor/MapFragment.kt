package com.parent.monitor

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

class MapFragment : Fragment(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private var childMarker: Marker? = null
    private var accuracyCircle: Circle? = null
    private var geofenceCircle: Circle? = null
    private var trailPolyline: Polyline? = null
    private val trailPoints = mutableListOf<LatLng>()
    private var mapReady = false
    private var pendingLat = 0.0
    private var pendingLng = 0.0
    private var pendingAcc = 0f
    private var hasPending = false

    companion object {
        var instance: MapFragment? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#060612"))
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val mapContainer = FrameLayout(ctx).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(mapContainer)

        val infoBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0A0A1E"))
            setPadding(20, 12, 20, 12)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val tvCoords = TextView(ctx).apply {
            id = R.id.tvMapCoords
            text = "Waiting for location..."
            textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val btnFetch = Button(ctx).apply {
            text = "📡 Fetch"
            textSize = 11f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setPadding(20, 8, 20, 8)
        }
        btnFetch.setOnClickListener {
            (activity as? MainActivity)?.wsManager?.sendCommand("get_location")
        }
        infoBar.addView(tvCoords)
        infoBar.addView(btnFetch)
        root.addView(infoBar)

        val mapFrag = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(mapContainer.id, mapFrag)
            .commit()
        mapFrag.getMapAsync(this)

        instance = this
        return root
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true
        try {
            map.setMapStyle(MapStyleOptions("""
                [{"elementType":"geometry","stylers":[{"color":"#0a0a1e"}]},
                 {"elementType":"labels.text.fill","stylers":[{"color":"#00e5ff"}]},
                 {"elementType":"labels.text.stroke","stylers":[{"color":"#0a0a1e"}]},
                 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1a1a3e"}]},
                 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#060612"}]},
                 {"featureType":"poi","stylers":[{"visibility":"off"}]}]
            """.trimIndent()))
        } catch (_: Exception) {}

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true

        if (hasPending) {
            updateMapLocation(pendingLat, pendingLng, pendingAcc)
            hasPending = false
        }
    }

    fun updateLocation(lat: Double, lng: Double, accuracy: Float) {
        activity?.runOnUiThread {
            view?.findViewById<TextView>(R.id.tvMapCoords)?.text =
                "📍 ${"%.5f".format(lat)}, ${"%.5f".format(lng)}  ±${accuracy.toInt()}m"
        }
        if (!mapReady) {
            pendingLat = lat; pendingLng = lng; pendingAcc = accuracy; hasPending = true
            return
        }
        activity?.runOnUiThread { updateMapLocation(lat, lng, accuracy) }
    }

    private fun updateMapLocation(lat: Double, lng: Double, accuracy: Float) {
        val pos = LatLng(lat, lng)
        trailPoints.add(pos)

        if (childMarker == null) {
            childMarker = googleMap?.addMarker(MarkerOptions()
                .position(pos)
                .title("Child Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)))
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
        } else {
            childMarker?.position = pos
            googleMap?.animateCamera(CameraUpdateFactory.newLatLng(pos))
        }

        accuracyCircle?.remove()
        accuracyCircle = googleMap?.addCircle(CircleOptions()
            .center(pos)
            .radius(accuracy.toDouble())
            .strokeColor(Color.parseColor("#4400E5FF"))
            .fillColor(Color.parseColor("#2200E5FF"))
            .strokeWidth(1f))

        trailPolyline?.remove()
        if (trailPoints.size > 1) {
            trailPolyline = googleMap?.addPolyline(PolylineOptions()
                .addAll(trailPoints)
                .color(Color.parseColor("#8800E5FF"))
                .width(4f)
                .geodesic(true))
        }
    }

    fun setGeofence(lat: Double, lng: Double, radiusMeters: Float) {
        if (!mapReady) return
        activity?.runOnUiThread {
            geofenceCircle?.remove()
            geofenceCircle = googleMap?.addCircle(CircleOptions()
                .center(LatLng(lat, lng))
                .radius(radiusMeters.toDouble())
                .strokeColor(Color.parseColor("#FF4CAF50"))
                .fillColor(Color.parseColor("#224CAF50"))
                .strokeWidth(2f))
        }
    }

    fun removeGeofence() {
        activity?.runOnUiThread { geofenceCircle?.remove(); geofenceCircle = null }
    }

    fun clearTrail() {
        trailPoints.clear()
        activity?.runOnUiThread { trailPolyline?.remove(); trailPolyline = null }
    }

    override fun onDestroyView() {
        instance = null
        googleMap = null
        mapReady = false
        super.onDestroyView()
    }
}
