package com.parent.monitor

import android.app.AlertDialog
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

    // Cache of package → friendly name received via "app_list" response
    private val appListCache = mutableListOf<Pair<String, String>>() // (package, name)
    // Pending app-list picker: if true, show picker as soon as app_list arrives
    private var pendingPickerRequest = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // ── Status label ─────────────────────────────────────────────────────
        tvStatus = TextView(ctx).apply {
            text = "Tap Fetch to load app usage"
            setTextColor(0xFF888888.toInt())
            textSize = 13f
        }
        root.addView(tvStatus)

        // ── Block App button (sends get_app_list → shows picker) ─────────────
        val btnBlock = Button(ctx).apply {
            text = "🚫 BLOCK APP (PICKER)"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnBlock.setOnClickListener {
            if (appListCache.isNotEmpty()) {
                showAppPickerDialog(act)
            } else {
                tvStatus?.text = "Loading app list…"
                pendingPickerRequest = true
                act.wsManager.sendCommand("get_app_list")
            }
        }
        root.addView(btnBlock, ViewGroup.LayoutParams(-1, -2))

        // ── Fetch app usage button ────────────────────────────────────────────
        val btn = Button(ctx).apply {
            text = "FETCH APP USAGE"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A33.toInt())
            setTextColor(0xFF00E5FF.toInt())
        }
        root.addView(btn, ViewGroup.LayoutParams(-1, -2))

        // ── Manual package-name input (fallback) ─────────────────────────────
        val etManual = EditText(ctx).apply {
            hint = "Or type package name (e.g. com.instagram.android)"
            setTextColor(0xFFCCCCCC.toInt())
            setHintTextColor(0xFF555577.toInt())
            textSize = 13f
        }
        root.addView(etManual, ViewGroup.LayoutParams(-1, -2))

        val btnManualBlock = Button(ctx).apply {
            text = "BLOCK TYPED PACKAGE"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF333355.toInt())
            setTextColor(0xFFFF8888.toInt())
        }
        btnManualBlock.setOnClickListener {
            val pkg = etManual.text.toString().trim()
            if (pkg.isNotEmpty()) {
                act.wsManager.sendCommand("block_app", org.json.JSONObject().apply { put("package", pkg) })
                tvStatus?.text = "Block request sent for: $pkg"
                etManual.setText("")
            }
        }
        root.addView(btnManualBlock, ViewGroup.LayoutParams(-1, -2))

        // ── App usage list ────────────────────────────────────────────────────
        lvApps = ListView(ctx)
        adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items)
        lvApps!!.adapter = adapter
        root.addView(lvApps, ViewGroup.LayoutParams(-1, -2))

        btn.setOnClickListener { tvStatus?.text = "Fetching…"; act.wsManager.sendCommand("get_app_usage") }

        (activity as? MainActivity)?.let { it.appsFragment = this }
        return root
    }

    /** Called by MainActivity when "app_list" command response arrives. */
    fun onAppList(arr: JSONArray) {
        appListCache.clear()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val pkg  = o.optString("package")
            val name = o.optString("name", pkg.substringAfterLast('.'))
            if (pkg.isNotEmpty()) appListCache.add(pkg to name)
        }
        if (pendingPickerRequest) {
            pendingPickerRequest = false
            val act = activity as? MainActivity ?: return
            requireActivity().runOnUiThread { showAppPickerDialog(act) }
        }
    }

    /** Called by MainActivity when "app_usage" stats arrive. */
    fun onData(arr: JSONArray) {
        items.clear()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            items.add("${o.optString("package")} — ${o.optInt("minutes")}min")
        }
        adapter?.notifyDataSetChanged()
        tvStatus?.text = "${items.size} apps"
    }

    private fun showAppPickerDialog(act: MainActivity) {
        if (!isAdded) return
        val ctx = requireContext()
        val names  = appListCache.map { (pkg, name) -> name }.toTypedArray()
        val pkgs   = appListCache.map { (pkg, _) -> pkg }
        val labels = appListCache.map { (pkg, name) ->
            // Two-line display built into a single string — ListView formats it
            "$name\n$pkg"
        }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("Select app to block")
            .setItems(labels) { _, which ->
                val pkg  = pkgs[which]
                val name = names[which]
                act.wsManager.sendCommand("block_app",
                    org.json.JSONObject().apply { put("package", pkg) })
                tvStatus?.text = "Blocked: $name ($pkg)"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { (activity as? MainActivity)?.appsFragment = null; super.onDestroyView() }
}