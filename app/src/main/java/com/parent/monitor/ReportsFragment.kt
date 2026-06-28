package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class ReportsFragment : Fragment() {

    private var tvReportContent: TextView? = null
    private var tvReportStatus: TextView? = null
    private var rvAlerts: RecyclerView? = null

    private val alertItems = mutableListOf<AlertItem>()
    private var alertAdapter: AlertAdapter? = null

    // ── Data model ──────────────────────────────────────────────────────────
    data class AlertItem(val time: String, val type: String, val message: String)

    // ── RecyclerView Adapter ────────────────────────────────────────────────
    inner class AlertAdapter : RecyclerView.Adapter<AlertAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime:    TextView = itemView.findViewById(R.id.tvAlertTime)
            val tvType:    TextView = itemView.findViewById(R.id.tvAlertType)
            val tvMessage: TextView = itemView.findViewById(R.id.tvAlertMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alert_card, parent, false)
            return VH(v)
        }

        override fun getItemCount() = alertItems.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = alertItems[position]
            holder.tvTime.text    = item.time
            holder.tvType.text    = item.type
            holder.tvMessage.text = item.message
            holder.tvType.setTextColor(colorForType(item.type))
        }

        private fun colorForType(type: String): Int {
            val t = type.lowercase()
            return when {
                listOf("grooming", "sos", "uninstall", "block").any { t.contains(it) } ->
                    0xFFEF4444.toInt()  // Red
                listOf("keyword", "permission", "geofence").any { t.contains(it) } ->
                    0xFFF97316.toInt()  // Orange
                listOf("online", "token").any { t.contains(it) } ->
                    0xFF22C55E.toInt()  // Green
                else ->
                    0xFF38BDF8.toInt()  // Blue
            }
        }
    }

    // ── Fragment lifecycle ──────────────────────────────────────────────────
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_reports, c, false)
        val act = requireActivity() as MainActivity

        tvReportContent = v.findViewById(R.id.tvReportContent)
        tvReportStatus  = v.findViewById(R.id.tvReportStatus)
        rvAlerts        = v.findViewById(R.id.rvAlerts)

        alertAdapter = AlertAdapter()
        rvAlerts?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
        }

        v.findViewById<Button>(R.id.btnGetReport).setOnClickListener {
            act.wsManager?.sendCommand("get_daily_report")
            tvReportStatus?.text = "Fetching report..."
        }

        v.findViewById<Button>(R.id.btnClearAlerts).setOnClickListener {
            alertItems.clear()
            alertAdapter?.notifyDataSetChanged()
            tvReportStatus?.text = "Ready"
            tvReportStatus?.setTextColor(0xFF555555.toInt())
        }

        (activity as? MainActivity)?.reportsFragment = this
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.reportsFragment = null
        tvReportContent = null
        tvReportStatus  = null
        rvAlerts        = null
    }

    // ── Public API (signature unchanged) ────────────────────────────────────
    fun addAlert(type: String, message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        alertItems.add(0, AlertItem(time, type, message))
        if (alertItems.size > 200) alertItems.removeAt(alertItems.size - 1)
        alertAdapter?.notifyItemInserted(0)
        rvAlerts?.scrollToPosition(0)
        val count = alertItems.size
        tvReportStatus?.text = "⚠️ $count alerts total"
        tvReportStatus?.setTextColor(0xFFFFB300.toInt())
    }

    fun onDailyReport(data: JSONObject) {
        val sb = StringBuilder()
        sb.appendLine("📊 DAILY REPORT — ${data.optString("date")}")
        sb.appendLine("═══════════════════════")
        sb.appendLine("📱 Total screen time: ${data.optLong("total_minutes")} min")
        sb.appendLine("🚫 Apps blocked: ${data.optInt("blocked_count")}")
        sb.appendLine()
        sb.appendLine("TOP APPS:")
        val apps = data.optJSONArray("apps")
        if (apps != null) {
            for (idx in 0 until apps.length()) {
                val app  = apps.getJSONObject(idx)
                val name = app.optString("package").substringAfterLast('.').take(20)
                val mins = app.optLong("minutes")
                val bar  = "▓".repeat((mins / 5).toInt().coerceAtMost(20))
                sb.appendLine("  $name: ${mins}min $bar")
            }
        }
        tvReportContent?.text = sb.toString()
        tvReportStatus?.text = "✅ Report loaded"
        tvReportStatus?.setTextColor(0xFF4CAF50.toInt())
    }
}
