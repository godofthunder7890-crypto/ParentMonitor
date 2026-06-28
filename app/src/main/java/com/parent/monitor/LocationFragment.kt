package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

class LocationFragment : Fragment() {

    private var tvCoords: TextView? = null
    private var tvAccuracy: TextView? = null
    private var llHistory: LinearLayout? = null
    private val locationHistory = mutableListOf<String>()
    private var lastLat = 0.0
    private var lastLng = 0.0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF060612.toInt())
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0A0A1E.toInt())
            setPadding(28, 20, 28, 20)
        }
        val tvTitle = TextView(ctx).apply {
            text = "LIVE LOCATION (Google Maps)"; textSize = 12f
            setTextColor(0xFF00E5FF.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val btnFetch = Button(ctx).apply {
            text = "📍"; textSize = 14f; setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00E5FF.toInt()); setPadding(20, 8, 20, 8)
        }
        btnFetch.setOnClickListener { act.sendCommand("get_location") }
        header.addView(tvTitle); header.addView(btnFetch)
        root.addView(header)

        tvCoords = TextView(ctx).apply {
            text = "Waiting for location…"; textSize = 14f
            setTextColor(0xFF00E5FF.toInt()); setPadding(28, 10, 28, 0)
        }
        root.addView(tvCoords)

        tvAccuracy = TextView(ctx).apply {
            text = ""; textSize = 11f; setTextColor(0xFF555577.toInt()); setPadding(28, 2, 28, 6)
        }
        root.addView(tvAccuracy)

        val mapContainerId = View.generateViewId()
        val mapContainer = FrameLayout(ctx).apply {
            id = mapContainerId
            layoutParams = LinearLayout.LayoutParams(-1, 0).apply { weight = 1f }
        }
        root.addView(mapContainer)
        val mapFrag = MapFragment()
        childFragmentManager.beginTransaction()
            .replace(mapContainerId, mapFrag)
            .commit()

        val histHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0A0A1E.toInt())
            setPadding(28, 8, 28, 8)
        }
        histHeader.addView(TextView(ctx).apply {
            text = "History"; textSize = 11f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        val btnClear = Button(ctx).apply {
            text = "Clear"; textSize = 10f; setTextColor(0xFF888888.toInt())
            setBackgroundColor(0); setPadding(16, 4, 0, 4)
        }
        btnClear.setOnClickListener { locationHistory.clear(); llHistory?.removeAllViews() }
        histHeader.addView(btnClear)
        root.addView(histHeader)

        val scrollHist = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 120.dp(ctx))
        }
        llHistory = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(28, 4, 28, 8)
        }
        scrollHist.addView(llHistory)
        root.addView(scrollHist)

        (activity as? MainActivity)?.locationFragment = this
        return root
    }

    fun onLocation(lat: Double, lng: Double, acc: Float) {
        lastLat = lat; lastLng = lng
        activity?.runOnUiThread {
            val ls = "%.6f".format(lat)
            val ln = "%.6f".format(lng)
            tvCoords?.text = "📍 $ls, $ln"
            tvAccuracy?.text = if (acc > 0) "Accuracy: ±${acc.toInt()}m" else ""

            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val entry = "$time — $ls, $ln"
            if (locationHistory.firstOrNull() != entry) {
                locationHistory.add(0, entry)
                if (locationHistory.size > 25) locationHistory.removeAt(locationHistory.size - 1)
                val tv = TextView(requireContext()).apply {
                    text = entry; textSize = 10f; setTextColor(0xFF555577.toInt())
                    setPadding(0, 2, 0, 2)
                }
                llHistory?.addView(tv, 0)
                if ((llHistory?.childCount ?: 0) > 25) llHistory?.removeViewAt(25)
            }

            MapFragment.instance?.updateLocation(lat, lng, acc)
        }
    }

    private fun Int.dp(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        (activity as? MainActivity)?.locationFragment = null
        super.onDestroyView()
    }
}
