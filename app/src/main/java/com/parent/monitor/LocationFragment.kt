package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

class LocationFragment : Fragment() {
    private var tvLat: TextView? = null
    private var tvLng: TextView? = null
    private var tvAcc: TextView? = null
    private var tvHist: TextView? = null
    private val histLog = mutableListOf<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val root = LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        fun label(t:String) = TextView(ctx).apply { text=t; setTextColor(0xFF888888.toInt()); textSize=11f }
        fun value(id: Int) = TextView(ctx).apply { text="--"; setTextColor(0xFF00E5FF.toInt()); textSize=22f; typeface=android.graphics.Typeface.MONOSPACE }
        root.addView(label("LATITUDE")); tvLat = value(0); root.addView(tvLat)
        root.addView(label("LONGITUDE")); tvLng = value(1); root.addView(tvLng)
        root.addView(label("ACCURACY")); tvAcc = value(2); root.addView(tvAcc)
        val btnFetch = Button(ctx).apply { text="📍 FETCH LOCATION"; backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF001A33.toInt()); setTextColor(0xFF00E5FF.toInt()) }
        root.addView(btnFetch)
        tvHist = TextView(ctx).apply { text="No history"; setTextColor(0xFF444466.toInt()); textSize=12f; setPadding(0,16,0,0) }
        root.addView(tvHist)
        btnFetch.setOnClickListener { act.wsManager.sendCommand("get_location") }
        (activity as? MainActivity)?.locationFragment = this
        return root
    }

    fun onLocation(lat: Double, lng: Double, acc: Float = 0f) {
        tvLat?.text = "%.6f".format(lat)
        tvLng?.text = "%.6f".format(lng)
        if (acc > 0) tvAcc?.text = "±${acc.toInt()}m"
        histLog.add(0, "%.5f, %.5f".format(lat, lng))
        if (histLog.size > 20) histLog.removeAt(histLog.size - 1)
        tvHist?.text = histLog.joinToString("\n")
    }

    override fun onDestroyView() { (activity as? MainActivity)?.locationFragment = null; super.onDestroyView() }
}