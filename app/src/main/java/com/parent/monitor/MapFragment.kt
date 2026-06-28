package com.parent.monitor

import android.content.pm.PackageManager
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
    private var mapsEnabled = false

    companion object {
        var instance: MapFragment? = null

        /** Returns true only when the build-time API key is a non-empty string. */
        fun isMapsKeyPresent(ctx: android.content.Context): Boolean {
            return try {
                val ai = ctx.packageManager.getApplicationInfo(
                    ctx.packageName, PackageManager.GET_META_DATA)
                val key = ai.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
                key.isNotEmpty()
            } catch (_: Exception) { false }
        }
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

        // ── Info bar (coords + fetch button) — always shown ────────────────
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

        mapsEnabled = isMapsKeyPresent(ctx)

        if (mapsEnabled) {
            // ── Normal map path ────────────────────────────────────────────
            val mapContainer = FrameLayout(ctx).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            }
            root.addView(mapContainer)
            root.addView(infoBar)

            val mapFrag = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(mapContainer.id, mapFrag)
                .commit()
            mapFrag.getMapAsync(this)
        } else {
            // ── No-key placeholder — never crashes the app ─────────────────
            val placeholder = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Color.parseColor("#0D0D2B"))
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                setPadding(48, 48, 48, 48)
            }
            placeholder.addView(TextView(ctx).apply {
                text = "🗺️"
                textSize = 56f
                gravity = android.view.Gravity.CENTER
            })
            placeholder.addView(TextView(ctx).apply {
                text = "Google Maps API Key Required"
                textSize = 18f
                setTextColor(Color.parseColor("#00E5FF"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 16)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            placeholder.addView(TextView(ctx).apply {
                text = "To enable the map:\n\n" +
                    "1. Go to console.cloud.google.com\n" +
                    "2. Create a Maps SDK for Android API key\n" +
                    "3. Add it as GOOGLE_MAPS_API_KEY\n" +
                    "   in GitHub repo Settings → Secrets\n" +
                    "4. Push any commit to rebuild the APK\n\n" +
                    "📍 Location coordinates still work below."
                textSize = 13f
                setTextColor(Color.parseColor("#8899BB"))
                gravity = android.view.Gravity.CENTER
                lineSpacingMultiplier = 1.4f
            })
            root.addView(placeholder)
            root.addView(infoBar)
        }

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
