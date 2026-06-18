package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray

class FilesFragment : Fragment() {
    private var tvStatus: TextView? = null
    private var lvFiles: ListView? = null
    private val items = mutableListOf<String>()
    private var adapter: ArrayAdapter<String>? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val root = LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        tvStatus = TextView(ctx).apply { text="Tap Fetch to load files"; setTextColor(0xFF888888.toInt()); textSize=13f }
        root.addView(tvStatus)
        val btnFetch = Button(ctx).apply { text="FETCH FILES / GALLERY"; backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt()); setTextColor(0xFF00E5FF.toInt()) }
        root.addView(btnFetch)
        lvFiles = ListView(ctx); adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items); lvFiles!!.adapter = adapter
        root.addView(lvFiles, ViewGroup.LayoutParams(-1,-2))
        btnFetch.setOnClickListener { tvStatus?.text="Fetching..."; act.wsManager.sendCommand("get_gallery") }
        (activity as? MainActivity)?.filesFragment = this
        return root
    }

    fun onFiles(arr: JSONArray) {
        items.clear()
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k)
            if (o != null) items.add("${o.optString("name","file")} — ${o.optString("size","?")}") else items.add(arr.optString(k))
        }
        adapter?.notifyDataSetChanged()
        tvStatus?.text = "${items.size} files"
    }

    override fun onDestroyView() { (activity as? MainActivity)?.filesFragment = null; super.onDestroyView() }
}