package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class BrowserSafetyFragment : Fragment() {

    private var etDomain: EditText? = null
    private var tvBlockedList: TextView? = null
    private var tvAllowedList: TextView? = null
    private var tvLog: TextView? = null
    private val blockedDomains  = mutableListOf<String>()
    private val allowedDomains  = mutableListOf<String>()
    private val blockLog = mutableListOf<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 24)
        }
        scroll.addView(root)

        fun label(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFF888888.toInt()); textSize = 11f; setPadding(0, 14, 0, 4)
        }
        fun sectionHeader(t: String) = TextView(ctx).apply {
            text = t; setTextColor(0xFF10FF80.toInt()); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 20, 0, 4)
        }

        root.addView(TextView(ctx).apply {
            text = "🌐 Browser Safety"
            setTextColor(0xFFF1F5F9.toInt()); textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 4)
        })
        root.addView(TextView(ctx).apply {
            text = "Block harmful sites or restrict to only allowed websites"
            setTextColor(0xFF64748B.toInt()); textSize = 12f; setPadding(0, 0, 0, 16)
        })

        // ── Domain input ──────────────────────────────────────────────────────
        root.addView(label("DOMAIN / URL"))
        val inputRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        etDomain = EditText(ctx).apply {
            hint = "e.g. youtube.com or tiktok.com"
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF444444.toInt())
            setBackgroundColor(0xFF001A33.toInt()); setPadding(16, 12, 16, 12); textSize = 13f
        }
        inputRow.addView(etDomain, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(inputRow)

        // ── Block / Allow buttons ─────────────────────────────────────────────
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnBlock = Button(ctx).apply {
            text = "🚫 BLOCK"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
            setTextColor(0xFFFFFFFF.toInt()); textSize = 12f
        }
        val btnAllow = Button(ctx).apply {
            text = "✅ ALLOW"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
            setTextColor(0xFF000000.toInt()); textSize = 12f
        }

        fun getDomain(): String? {
            val d = etDomain?.text?.toString()?.trim()?.lowercase() ?: return null
            return if (d.isBlank()) null else d
        }

        btnBlock.setOnClickListener {
            val domain = getDomain() ?: run { Toast.makeText(ctx, "Enter a domain", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!blockedDomains.contains(domain)) {
                blockedDomains.add(domain); syncBlocked(act); refreshBlockedList(); etDomain?.text?.clear()
            } else Toast.makeText(ctx, "Already blocked", Toast.LENGTH_SHORT).show()
        }
        btnAllow.setOnClickListener {
            val domain = getDomain() ?: run { Toast.makeText(ctx, "Enter a domain", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!allowedDomains.contains(domain)) {
                allowedDomains.add(domain); syncAllowed(act); refreshAllowedList(); etDomain?.text?.clear()
            } else Toast.makeText(ctx, "Already allowed", Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,8,0) })
        btnRow.addView(btnAllow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(btnRow)

        // ── Whitelist-only mode ───────────────────────────────────────────────
        root.addView(sectionHeader("🔒 Whitelist Mode"))
        root.addView(TextView(ctx).apply {
            text = "When enabled, child's browser can ONLY visit allowed sites. All others blocked."
            setTextColor(0xFF94A3B8.toInt()); textSize = 12f; setPadding(0, 0, 0, 8)
        })
        val wlModeRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnWlOn = Button(ctx).apply {
            text = "🔒 Enable Whitelist Mode"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
            setTextColor(0xFF000000.toInt()); textSize = 11f
        }
        val btnWlOff = Button(ctx).apply {
            text = "🔓 Disable"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF334155.toInt())
            setTextColor(0xFFFFFFFF.toInt()); textSize = 11f
        }
        btnWlOn.setOnClickListener {
            act.sendCommandObj(JSONObject().apply { put("command", "set_browser_whitelist_mode"); put("enabled", true) })
            Toast.makeText(ctx, "🔒 Whitelist mode ON", Toast.LENGTH_SHORT).show()
        }
        btnWlOff.setOnClickListener {
            act.sendCommandObj(JSONObject().apply { put("command", "set_browser_whitelist_mode"); put("enabled", false) })
            Toast.makeText(ctx, "🔓 Whitelist mode OFF", Toast.LENGTH_SHORT).show()
        }
        wlModeRow.addView(btnWlOn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,8,0) })
        wlModeRow.addView(btnWlOff, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(wlModeRow)

        // ── DNS Filter ────────────────────────────────────────────────────────
        root.addView(sectionHeader("🛡 DNS Safety Filter"))
        val dnsRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnDnsOn = Button(ctx).apply {
            text = "🛡 Enable Family DNS"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1565C0.toInt())
            setTextColor(0xFFFFFFFF.toInt()); textSize = 11f
        }
        val btnDnsOff = Button(ctx).apply {
            text = "✕ Disable DNS"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF334155.toInt())
            setTextColor(0xFFFFFFFF.toInt()); textSize = 11f
        }
        btnDnsOn.setOnClickListener {
            act.sendCommandObj(JSONObject().apply {
                put("command", "set_dns"); put("primary", "1.1.1.3"); put("secondary", "1.0.0.3")
            })
            Toast.makeText(ctx, "Family DNS enabled (Cloudflare 1.1.1.3)", Toast.LENGTH_SHORT).show()
        }
        btnDnsOff.setOnClickListener {
            act.sendCommandObj(JSONObject().apply { put("command", "set_dns"); put("primary", ""); put("secondary", "") })
            Toast.makeText(ctx, "DNS filter disabled", Toast.LENGTH_SHORT).show()
        }
        dnsRow.addView(btnDnsOn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,8,0) })
        dnsRow.addView(btnDnsOff, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(dnsRow)

        // ── Fetch from child ──────────────────────────────────────────────────
        val btnFetch = Button(ctx).apply {
            text = "📋 Fetch Lists from Child"; setTextColor(0xFF00E5FF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF001A33.toInt())
        }
        btnFetch.setOnClickListener { act.sendCommand("get_browser_lists") }
        root.addView(btnFetch)

        // ── Blocked list ──────────────────────────────────────────────────────
        root.addView(sectionHeader("🚫 BLOCKED DOMAINS"))
        tvBlockedList = TextView(ctx).apply {
            text = "No blocked domains yet\n(tap to remove items)"
            setTextColor(0xFFFF1744.toInt()); textSize = 12f
            setBackgroundColor(0xFF1A0000.toInt()); setPadding(16, 12, 16, 12)
        }
        tvBlockedList?.setOnClickListener { showRemoveDialog(act, blocked = true) }
        root.addView(tvBlockedList)

        // ── Allowed list ──────────────────────────────────────────────────────
        root.addView(sectionHeader("✅ ALLOWED DOMAINS (Whitelist)"))
        tvAllowedList = TextView(ctx).apply {
            text = "No allowed domains yet\n(tap to remove items)"
            setTextColor(0xFF00C853.toInt()); textSize = 12f
            setBackgroundColor(0xFF001A0D.toInt()); setPadding(16, 12, 16, 12)
        }
        tvAllowedList?.setOnClickListener { showRemoveDialog(act, blocked = false) }
        root.addView(tvAllowedList)

        // ── Activity log ──────────────────────────────────────────────────────
        root.addView(sectionHeader("📋 BLOCKED ACCESS LOG"))
        tvLog = TextView(ctx).apply {
            text = "No blocks recorded yet"
            setTextColor(0xFFFF1744.toInt()); textSize = 11f
            setBackgroundColor(0xFF110005.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvLog)

        (activity as? MainActivity)?.browserSafetyFragment = this
        return scroll
    }

    private fun syncBlocked(act: MainActivity) {
        act.sendCommandObj(JSONObject().apply {
            put("command", "set_blocked_domains"); put("domains", JSONArray(blockedDomains))
        })
    }

    private fun syncAllowed(act: MainActivity) {
        act.sendCommandObj(JSONObject().apply {
            put("command", "set_allowed_domains"); put("domains", JSONArray(allowedDomains))
        })
    }

    fun onBlockedDomains(domains: List<String>) {
        blockedDomains.clear(); blockedDomains.addAll(domains); refreshBlockedList()
    }

    fun onAllowedDomains(domains: List<String>) {
        allowedDomains.clear(); allowedDomains.addAll(domains); refreshAllowedList()
    }

    fun onBrowserBlocked(url: String, domain: String, pkg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        blockLog.add(0, "[$ts] Blocked: $domain\n  URL: ${url.take(60)}\n  App: ${pkg.substringAfterLast('.')}")
        if (blockLog.size > 50) blockLog.removeAt(blockLog.size - 1)
        tvLog?.text = blockLog.joinToString("\n\n")
    }

    private fun refreshBlockedList() {
        tvBlockedList?.text = if (blockedDomains.isEmpty())
            "No blocked domains yet\n(tap to remove items)"
        else blockedDomains.mapIndexed { i, d -> "${i+1}. $d" }.joinToString("\n")
    }

    private fun refreshAllowedList() {
        tvAllowedList?.text = if (allowedDomains.isEmpty())
            "No allowed domains yet\n(tap to remove items)"
        else allowedDomains.mapIndexed { i, d -> "${i+1}. ✅ $d" }.joinToString("\n")
    }

    private fun showRemoveDialog(act: MainActivity, blocked: Boolean) {
        val list = if (blocked) blockedDomains else allowedDomains
        if (list.isEmpty()) return
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(if (blocked) "Remove Blocked Domain" else "Remove Allowed Domain")
            .setItems(list.toTypedArray()) { _, which ->
                list.removeAt(which)
                if (blocked) { syncBlocked(act); refreshBlockedList() }
                else         { syncAllowed(act); refreshAllowedList() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.browserSafetyFragment = null
        super.onDestroyView()
    }
}
