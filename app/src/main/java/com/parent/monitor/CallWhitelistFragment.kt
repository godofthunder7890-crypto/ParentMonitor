package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class CallWhitelistFragment : Fragment() {

    private var etNumber: EditText? = null
    private var etName: EditText? = null
    private var tvWhitelistDisplay: TextView? = null
    private var tvBlacklistDisplay: TextView? = null
    private val whitelist = mutableListOf<Pair<String,String>>()  // number, name
    private val blacklist = mutableListOf<Pair<String,String>>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity
        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 24)
        }
        scroll.addView(root)

        fun sectionHeader(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFF10FF80.toInt()); textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
        fun label(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFF888888.toInt()); textSize = 11f
            setPadding(0, 12, 0, 4)
        }

        root.addView(TextView(ctx).apply {
            text = "📞 Call Whitelist & Blacklist"
            setTextColor(0xFFF1F5F9.toInt()); textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 4)
        })
        root.addView(TextView(ctx).apply {
            text = "Whitelist = only these numbers can call child  •  Blacklist = block specific numbers"
            setTextColor(0xFF64748B.toInt()); textSize = 12f; setPadding(0, 0, 0, 16)
        })

        // ── Input row ─────────────────────────────────────────────────────────
        root.addView(label("PHONE NUMBER"))
        etNumber = EditText(ctx).apply {
            hint = "+91XXXXXXXXXX or 10-digit"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF444444.toInt())
            setBackgroundColor(0xFF001A33.toInt()); setPadding(16, 12, 16, 12); textSize = 13f
        }
        root.addView(etNumber)
        root.addView(label("NAME / LABEL (optional)"))
        etName = EditText(ctx).apply {
            hint = "e.g. Mom, Dad, Teacher"
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF444444.toInt())
            setBackgroundColor(0xFF001A33.toInt()); setPadding(16, 12, 16, 12); textSize = 13f
        }
        root.addView(etName)

        // ── Whitelist / Blacklist add buttons ─────────────────────────────────
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnWhite = Button(ctx).apply {
            text = "✅ Add to Whitelist"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
            setTextColor(0xFF000000.toInt()); textSize = 12f
        }
        val btnBlack = Button(ctx).apply {
            text = "🚫 Add to Blacklist"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
            setTextColor(0xFFFFFFFF.toInt()); textSize = 12f
        }

        fun getNum(): String? {
            val n = etNumber?.text?.toString()?.trim() ?: return null
            return if (n.isBlank()) null else n
        }
        fun getName(): String = etName?.text?.toString()?.trim() ?: ""

        btnWhite.setOnClickListener {
            val num = getNum() ?: run {
                Toast.makeText(ctx, "Enter a phone number first", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val name = getName()
            if (whitelist.none { it.first == num }) {
                whitelist.add(Pair(num, name))
                syncWhitelist(act); refreshWhitelistUI(); etNumber?.text?.clear(); etName?.text?.clear()
            } else Toast.makeText(ctx, "Already in whitelist", Toast.LENGTH_SHORT).show()
        }
        btnBlack.setOnClickListener {
            val num = getNum() ?: run {
                Toast.makeText(ctx, "Enter a phone number first", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val name = getName()
            if (blacklist.none { it.first == num }) {
                blacklist.add(Pair(num, name))
                syncBlacklist(act); refreshBlacklistUI(); etNumber?.text?.clear(); etName?.text?.clear()
            } else Toast.makeText(ctx, "Already in blacklist", Toast.LENGTH_SHORT).show()
        }

        btnRow.addView(btnWhite, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,8,0) })
        btnRow.addView(btnBlack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(btnRow)

        // ── Fetch from child ──────────────────────────────────────────────────
        val btnFetch = Button(ctx).apply {
            text = "📋 Fetch Lists from Child"; setTextColor(0xFF00E5FF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF001A33.toInt())
        }
        btnFetch.setOnClickListener { act.sendCommand("get_call_lists") }
        root.addView(btnFetch)

        // ── Whitelist mode toggle ─────────────────────────────────────────────
        root.addView(sectionHeader("WHITELIST MODE (allow-only)"))
        root.addView(TextView(ctx).apply {
            text = "When enabled, child can ONLY receive/make calls to whitelisted numbers. All others are blocked."
            setTextColor(0xFF94A3B8.toInt()); textSize = 12f; setPadding(0, 0, 0, 8)
        })
        val modeRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnModeOn = Button(ctx).apply {
            text = "🔒 Enable Whitelist Mode"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
            setTextColor(0xFF000000.toInt()); textSize = 11f
        }
        val btnModeOff = Button(ctx).apply {
            text = "🔓 Disable"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF334155.toInt())
            setTextColor(0xFFFFFFFF.toInt()); textSize = 11f
        }
        btnModeOn.setOnClickListener {
            act.sendCommandObj(JSONObject().apply { put("command", "set_whitelist_mode"); put("enabled", true) })
            Toast.makeText(ctx, "🔒 Whitelist mode ON — only approved numbers allowed", Toast.LENGTH_SHORT).show()
        }
        btnModeOff.setOnClickListener {
            act.sendCommandObj(JSONObject().apply { put("command", "set_whitelist_mode"); put("enabled", false) })
            Toast.makeText(ctx, "🔓 Whitelist mode OFF — blacklist only", Toast.LENGTH_SHORT).show()
        }
        modeRow.addView(btnModeOn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,8,0) })
        modeRow.addView(btnModeOff, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(modeRow)

        // ── Whitelist display ──────────────────────────────────────────────────
        root.addView(sectionHeader("✅ WHITELISTED NUMBERS"))
        tvWhitelistDisplay = TextView(ctx).apply {
            text = "No numbers whitelisted yet\nTap above to add"
            setTextColor(0xFF00C853.toInt()); textSize = 12f
            setBackgroundColor(0xFF001A0D.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvWhitelistDisplay)

        // ── Blacklist display ─────────────────────────────────────────────────
        root.addView(sectionHeader("🚫 BLACKLISTED NUMBERS"))
        tvBlacklistDisplay = TextView(ctx).apply {
            text = "No numbers blacklisted yet\nTap above to add"
            setTextColor(0xFFFF1744.toInt()); textSize = 12f
            setBackgroundColor(0xFF1A0000.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvBlacklistDisplay)

        // Tap to remove items
        tvWhitelistDisplay?.setOnClickListener { showRemoveDialog(act, isWhitelist = true) }
        tvBlacklistDisplay?.setOnClickListener { showRemoveDialog(act, isWhitelist = false) }

        (activity as? MainActivity)?.callWhitelistFragment = this
        return scroll
    }

    private fun syncWhitelist(act: MainActivity) {
        val arr = JSONArray().also { a -> whitelist.forEach { (num, name) ->
            a.put(JSONObject().apply { put("number", num); put("name", name) })
        }}
        act.sendCommandObj(JSONObject().apply { put("command", "set_call_whitelist"); put("numbers", arr) })
    }

    private fun syncBlacklist(act: MainActivity) {
        val arr = JSONArray().also { a -> blacklist.forEach { (num, name) ->
            a.put(JSONObject().apply { put("number", num); put("name", name) })
        }}
        act.sendCommandObj(JSONObject().apply { put("command", "set_call_blacklist"); put("numbers", arr) })
    }

    private fun refreshWhitelistUI() {
        if (whitelist.isEmpty()) {
            tvWhitelistDisplay?.text = "No numbers whitelisted yet\nTap above to add"
        } else {
            tvWhitelistDisplay?.text = whitelist.joinToString("\n") { (num, name) ->
                if (name.isNotBlank()) "✅ $name — $num" else "✅ $num"
            }
        }
    }

    private fun refreshBlacklistUI() {
        if (blacklist.isEmpty()) {
            tvBlacklistDisplay?.text = "No numbers blacklisted yet\nTap above to add"
        } else {
            tvBlacklistDisplay?.text = blacklist.joinToString("\n") { (num, name) ->
                if (name.isNotBlank()) "🚫 $name — $num" else "🚫 $num"
            }
        }
    }

    private fun showRemoveDialog(act: MainActivity, isWhitelist: Boolean) {
        val list = if (isWhitelist) whitelist else blacklist
        if (list.isEmpty()) return
        val items = list.map { (num, name) -> if (name.isNotBlank()) "$name — $num" else num }.toTypedArray()
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(if (isWhitelist) "Remove from Whitelist" else "Remove from Blacklist")
            .setItems(items) { _, which ->
                list.removeAt(which)
                if (isWhitelist) { syncWhitelist(act); refreshWhitelistUI() }
                else             { syncBlacklist(act); refreshBlacklistUI() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun onCallLists(whiteArr: org.json.JSONArray?, blackArr: org.json.JSONArray?) {
        whitelist.clear(); blacklist.clear()
        whiteArr?.let { a -> (0 until a.length()).forEach { i ->
            val o = a.optJSONObject(i)
            if (o != null) whitelist.add(Pair(o.optString("number"), o.optString("name", "")))
            else whitelist.add(Pair(a.optString(i), ""))
        }}
        blackArr?.let { a -> (0 until a.length()).forEach { i ->
            val o = a.optJSONObject(i)
            if (o != null) blacklist.add(Pair(o.optString("number"), o.optString("name", "")))
            else blacklist.add(Pair(a.optString(i), ""))
        }}
        refreshWhitelistUI(); refreshBlacklistUI()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.callWhitelistFragment = null
        super.onDestroyView()
    }
}
