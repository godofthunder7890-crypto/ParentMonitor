package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject

class VideoHistoryFragment : Fragment() {

    private var tvHistory: TextView? = null
    private var tvStats: TextView? = null
    private val entries = mutableListOf<VideoEntry>()

    data class VideoEntry(
        val platform: String,
        val title: String,
        val pkg: String,
        val watchedSec: Long,
        val time: Long
    )

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

        // ── Stats ─────────────────────────────────────────────────────────────
        root.addView(label("TODAY'S VIDEO STATS"))
        tvStats = TextView(ctx).apply {
            text = "YouTube: 0 videos  |  TikTok: 0 videos"
            setTextColor(0xFF00E5FF.toInt()); textSize = 13f
            setBackgroundColor(0xFF001A33.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvStats)

        // ── Clear / refresh buttons ───────────────────────────────────────────
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val btnClear = Button(ctx).apply {
            text = "CLEAR LIST"; setTextColor(0xFFFF1744.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF110005.toInt())
        }
        btnClear.setOnClickListener { entries.clear(); refreshView() }
        btnRow.addView(btnClear)
        root.addView(btnRow)

        // ── History list ──────────────────────────────────────────────────────
        root.addView(label("VIDEO WATCH HISTORY (newest first)"))
        tvHistory = TextView(ctx).apply {
            text = "No videos watched yet"
            setTextColor(0xFFCCCCCC.toInt()); textSize = 12f
            setBackgroundColor(0xFF001122.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvHistory)

        (activity as? MainActivity)?.videoHistoryFragment = this
        return scroll
    }

    fun onVideoHistory(data: JSONObject) {
        val platform    = data.optString("platform", "unknown")
        val title       = data.optString("title", "—")
        val pkg         = data.optString("package", "")
        val watchedSec  = data.optLong("watched_seconds", 0L)
        val time        = data.optLong("time", System.currentTimeMillis())

        entries.add(0, VideoEntry(platform, title, pkg, watchedSec, time))
        if (entries.size > 200) entries.removeAt(entries.size - 1)
        refreshView()
    }

    private fun refreshView() {
        if (entries.isEmpty()) {
            tvHistory?.text = "No videos watched yet"
            tvStats?.text   = "YouTube: 0 videos  |  TikTok: 0 videos"
            return
        }

        val ytCount  = entries.count { it.platform == "youtube" }
        val ttCount  = entries.count { it.platform == "tiktok" }
        tvStats?.text = "YouTube: $ytCount videos  |  TikTok: $ttCount videos  |  Total: ${entries.size}"

        val sb  = StringBuilder()
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        entries.take(100).forEach { e ->
            val icon    = if (e.platform == "youtube") "▶ YT" else "♪ TT"
            val timeStr = sdf.format(java.util.Date(e.time))
            val dur     = if (e.watchedSec > 0) " (${e.watchedSec}s)" else ""
            sb.append("$icon  [$timeStr]$dur\n${e.title}\n\n")
        }
        tvHistory?.text = sb.toString().trimEnd()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.videoHistoryFragment = null
        super.onDestroyView()
    }
}
