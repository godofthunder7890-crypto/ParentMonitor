package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class BrowserSafetyFragment : Fragment() {

    private var etDomain: EditText? = null
    private var tvList: TextView? = null
    private var tvLog: TextView? = null
    private val blockedDomains = mutableListOf<String>()
    private val blockLog = mutableListOf<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
        }
        scroll.addView(root)

        fun label(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFF888888.toInt()); textSize = 11f; setPadding(0, 12, 0, 4)
        }

        // ── Add domain row ────────────────────────────────────────────────────
        root.addView(label("ADD BLOCKED DOMAIN / URL"))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        etDomain = EditText(ctx).apply {
            hint = "e.g. youtube.com or tiktok.com"
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF444444.toInt())
            setBackgroundColor(0xFF001A33.toInt())
            setPadding(16, 12, 16, 12); textSize = 13f
        }
        row.addView(etDomain, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btnAdd = Button(ctx).apply {
            text = "BLOCK"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnAdd.setOnClickListener {
            val domain = etDomain?.text?.toString()?.trim()?.lowercase() ?: return@setOnClickListener
            if (domain.isBlank()) return@setOnClickListener
            if (!blockedDomains.contains(domain)) {
                blockedDomains.add(domain)
                syncDomains(act)
                refreshList()
                etDomain?.text?.clear()
            }
        }
        row.addView(btnAdd)
        root.addView(row)

        // Feature F4: DNS Filter buttons (Cloudflare Family DNS)
        root.addView(label("DNS SAFETY FILTER"))
        val dnsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            (layoutParams as? LinearLayout.LayoutParams)?.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val btnDnsOn = Button(ctx).apply {
            text = "🛡 Enable Family DNS"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnDnsOn.setOnClickListener {
            act.sendCommandObj(JSONObject().apply {
                put("command", "set_dns")
                put("primary", "1.1.1.3"); put("secondary", "1.0.0.3")
            })
            android.widget.Toast.makeText(ctx, "Family DNS enabled (Cloudflare 1.1.1.3)", android.widget.Toast.LENGTH_SHORT).show()
        }
        val btnDnsOff = Button(ctx).apply {
            text = "✕ Disable DNS"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnDnsOff.setOnClickListener {
            act.sendCommandObj(JSONObject().apply {
                put("command", "set_dns")
                put("primary", ""); put("secondary", "")
            })
            android.widget.Toast.makeText(ctx, "DNS filter disabled", android.widget.Toast.LENGTH_SHORT).show()
        }
        dnsRow.addView(btnDnsOn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dnsRow.addView(btnDnsOff, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(dnsRow)

        // ── Fetch from child ──────────────────────────────────────────────────
        val btnFetch = Button(ctx).apply {
            text = "📋 FETCH FROM CHILD"; setTextColor(0xFF00E5FF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF001A33.toInt())
        }
        btnFetch.setOnClickListener { act.sendCommand("get_blocked_domains") }
        root.addView(btnFetch)

        // ── Blocked list ──────────────────────────────────────────────────────
        root.addView(label("CURRENTLY BLOCKED DOMAINS"))
        tvList = TextView(ctx).apply {
            text = "No blocked domains yet"
            setTextColor(0xFF00E5FF.toInt()); textSize = 12f
            setBackgroundColor(0xFF001122.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvList)

        // ── Activity log ──────────────────────────────────────────────────────
        root.addView(label("BLOCKED ACCESS LOG"))
        tvLog = TextView(ctx).apply {
            text = "No blocks recorded yet"
            setTextColor(0xFFFF1744.toInt()); textSize = 11f
            setBackgroundColor(0xFF110005.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvLog)

        (activity as? MainActivity)?.browserSafetyFragment = this
        return scroll
    }

    private fun syncDomains(act: MainActivity) {
        act.sendCommandObj(JSONObject().apply {
            put("command", "set_blocked_domains")
            put("domains", JSONArray(blockedDomains))
        })
    }

    fun onBlockedDomains(domains: List<String>) {
        blockedDomains.clear(); blockedDomains.addAll(domains)
        refreshList()
    }

    fun onBrowserBlocked(url: String, domain: String, pkg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        blockLog.add(0, "[$ts] Blocked: $domain\n  URL: ${url.take(60)}\n  App: ${pkg.substringAfterLast('.')}")
        if (blockLog.size > 50) blockLog.removeAt(blockLog.size - 1)
        tvLog?.text = blockLog.joinToString("\n\n")
    }

    private fun refreshList() {
        if (blockedDomains.isEmpty()) {
            tvList?.text = "No blocked domains yet"
            tvList?.setOnClickListener(null)
            return
        }
        val sb = StringBuilder()
        blockedDomains.forEachIndexed { idx, d ->
            sb.append("${idx + 1}. $d\n")
        }
        tvList?.text = sb.toString().trimEnd()

        // FIX #14: Show AlertDialog listing all domains so user can pick which one to remove
        tvList?.setOnClickListener {
            val act = activity as? MainActivity ?: return@setOnClickListener
            val items = blockedDomains.toTypedArray()
            android.app.AlertDialog.Builder(act)
                .setTitle("Remove Blocked Domain")
                .setItems(items) { _, which ->
                    blockedDomains.removeAt(which)
                    syncDomains(act)
                    refreshList()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.browserSafetyFragment = null
        super.onDestroyView()
    }
}
