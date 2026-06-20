package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject

class ReportsFragment : Fragment() {

    private var tvReportContent: TextView? = null
    private var tvReportStatus: TextView? = null
    private var lvAlerts: ListView? = null
    private val alertsList = mutableListOf<String>()
    private var alertAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_reports, c, false)
        val act = requireActivity() as MainActivity

        tvReportContent = v.findViewById(R.id.tvReportContent)
        tvReportStatus  = v.findViewById(R.id.tvReportStatus)
        lvAlerts        = v.findViewById(R.id.lvAlerts)

        alertAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, alertsList)
        lvAlerts?.adapter = alertAdapter

        v.findViewById<Button>(R.id.btnGetReport).setOnClickListener {
            act.wsManager?.sendCommand("get_daily_report")
            tvReportStatus?.text = "Fetching report..."
        }

        v.findViewById<Button>(R.id.btnClearAlerts).setOnClickListener {
            alertsList.clear(); alertAdapter?.notifyDataSetChanged()
        }

        (activity as? MainActivity)?.reportsFragment = this
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.reportsFragment = null
        tvReportContent = null; tvReportStatus = null; lvAlerts = null
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
                val app = apps.getJSONObject(idx)
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

    fun addAlert(type: String, message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        alertsList.add(0, "[$time] $type: $message")
        if (alertsList.size > 200) alertsList.removeAt(alertsList.size - 1)
        alertAdapter?.notifyDataSetChanged()
        val count = alertsList.size
        tvReportStatus?.text = "⚠️ $count alerts total"
        tvReportStatus?.setTextColor(0xFFFFB300.toInt())
    }
}
