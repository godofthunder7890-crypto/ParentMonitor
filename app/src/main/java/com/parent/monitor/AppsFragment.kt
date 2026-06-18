package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class AppsFragment : Fragment() {
    private var tvStatus: TextView? = null
    private var lvApps: ListView? = null
    private val items = mutableListOf<String>()
    private var adapter: ArrayAdapter<String>? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        tvStatus = TextView(ctx).apply { text = "Tap Fetch to load app usage"; setTextColor(0xFF888888.toInt()); textSize = 13f }
        root.addView(tvStatus)
        val btn = Button(ctx).apply { text = "FETCH APP USAGE"; backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt()); setTextColor(0xFF00E5FF.toInt()) }
        root.addView(btn)
        lvApps = ListView(ctx)
        adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items)
        lvApps!!.adapter = adapter
        root.addView(lvApps, ViewGroup.LayoutParams(-1, -2))
        btn.setOnClickListener { tvStatus?.text = "Fetching..."; act.wsManager.sendCommand("get_app_usage") }
        (activity as? MainActivity)?.let { it.appsFragment = this }
        return root
    }

    fun onData(arr: JSONArray) {
        items.clear()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            items.add("${o.optString("package")} — ${o.optInt("minutes")}min")
        }
        adapter?.notifyDataSetChanged()
        tvStatus?.text = "${items.size} apps"
    }

    override fun onDestroyView() { (activity as? MainActivity)?.appsFragment = null; super.onDestroyView() }
}