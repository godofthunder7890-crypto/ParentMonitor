package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray

class CallSmsFragment : Fragment() {
    private var tvStatus: TextView? = null
    private var lvItems: ListView? = null
    private val items = mutableListOf<String>()
    private var adapter: ArrayAdapter<String>? = null
    private var currentTab = "calls"

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCalls = Button(ctx).apply { text = "CALLS"; backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt()); setTextColor(0xFF000000.toInt()); layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
        val btnSms = Button(ctx).apply { text = "SMS"; backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt()); setTextColor(0xFF888888.toInt()); layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
        row.addView(btnCalls); row.addView(btnSms); root.addView(row)
        tvStatus = TextView(ctx).apply { text = "Tap Fetch"; setTextColor(0xFF888888.toInt()); textSize = 12f; setPadding(0,8,0,8) }
        root.addView(tvStatus)
        val btnFetch = Button(ctx).apply { text = "FETCH"; backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt()); setTextColor(0xFF00E5FF.toInt()) }
        root.addView(btnFetch)
        lvItems = ListView(ctx); adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items); lvItems!!.adapter = adapter
        root.addView(lvItems, ViewGroup.LayoutParams(-1,-2))
        btnCalls.setOnClickListener { currentTab="calls"; btnCalls.backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt()); btnCalls.setTextColor(0xFF000000.toInt()); btnSms.backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt()); btnSms.setTextColor(0xFF888888.toInt()) }
        btnSms.setOnClickListener { currentTab="sms"; btnSms.backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt()); btnSms.setTextColor(0xFF000000.toInt()); btnCalls.backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt()); btnCalls.setTextColor(0xFF888888.toInt()) }
        btnFetch.setOnClickListener { tvStatus?.text="Fetching $currentTab..."; if(currentTab=="calls") act.wsManager.sendCommand("get_call_log") else act.wsManager.sendCommand("get_sms") }
        (activity as? MainActivity)?.callSmsFragment = this
        return root
    }

    fun onCallData(arr: JSONArray) { items.clear(); for(k in 0 until arr.length()){ val o=arr.getJSONObject(k); items.add("${o.optString("name","?")} — ${o.optString("type")} — ${o.optString("duration","?")}") }; adapter?.notifyDataSetChanged(); tvStatus?.text="${items.size} calls" }
    fun onSmsData(arr: JSONArray) { items.clear(); for(k in 0 until arr.length()){ val o=arr.getJSONObject(k); items.add("${o.optString("address","?")} — ${o.optString("body","").take(40)}") }; adapter?.notifyDataSetChanged(); tvStatus?.text="${items.size} sms" }
    override fun onDestroyView() { (activity as? MainActivity)?.callSmsFragment = null; super.onDestroyView() }
}