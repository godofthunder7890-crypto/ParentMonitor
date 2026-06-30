package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SocialDetectFragment : Fragment() {

    data class Alert(
        val app:      String,
        val category: String,
        val severity: String,
        val keyword:  String,
        val preview:  String,
        val datetime: String,
        val ts:       Long
    )

    private val alerts  = mutableListOf<Alert>()
    private var adapter: AlertAdapter? = null
    private var tvStatus: TextView? = null
    private var swEnabled: Switch? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_social_detect, c, false)
        val act = requireActivity() as MainActivity

        tvStatus  = v.findViewById(R.id.tvSocialStatus)
        swEnabled = v.findViewById(R.id.swSocialEnabled)

        v.findViewById<Button>(R.id.btnSocialApply).setOnClickListener {
            val on = swEnabled?.isChecked ?: false
            act.sendCommandObj(JSONObject().apply {
                put("command", "social_detect_config"); put("enabled", on)
            })
            tvStatus?.text = if (on) "🔍 Social detection: ON" else "🔍 Social detection: OFF"
        }

        v.findViewById<Button>(R.id.btnSocialClear).setOnClickListener {
            alerts.clear(); adapter?.notifyDataSetChanged()
            tvStatus?.text = "Cleared"
        }

        val lv = v.findViewById<ListView>(R.id.lvSocialAlerts)
        adapter = AlertAdapter()
        lv.adapter = adapter

        return v
    }

    fun onSocialAlert(msg: JSONObject) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val alert = Alert(
            app      = msg.optString("app",      "Unknown"),
            category = msg.optString("category", "?"),
            severity = msg.optString("severity", "MEDIUM"),
            keyword  = msg.optString("keyword",  ""),
            preview  = msg.optString("preview",  ""),
            datetime = fmt.format(Date(msg.optLong("time", System.currentTimeMillis()))),
            ts       = msg.optLong("time", System.currentTimeMillis())
        )
        alerts.add(0, alert)
        if (alerts.size > 200) alerts.removeAt(alerts.size - 1)
        activity?.runOnUiThread {
            adapter?.notifyDataSetChanged()
            tvStatus?.text = "⚠️ ${alert.severity}: ${alert.category} in ${alert.app}"
        }
    }

    inner class AlertAdapter : BaseAdapter() {
        override fun getCount() = alerts.size
        override fun getItem(p: Int) = alerts[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(pos: Int, conv: View?, parent: ViewGroup): View {
            val row = conv ?: LayoutInflater.from(context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val a = alerts[pos]
            val color = if (a.severity == "HIGH") 0xFFFF5252.toInt() else 0xFFFFB300.toInt()
            row.findViewById<TextView>(android.R.id.text1).apply {
                text = "[${a.severity}] ${a.category.uppercase()} • ${a.app} • ${a.datetime}"
                setTextColor(color)
            }
            row.findViewById<TextView>(android.R.id.text2).apply {
                text = "Keyword: \"${a.keyword}\" — ${a.preview.take(80)}"
                setTextColor(0xFF999999.toInt())
            }
            row.setBackgroundColor(if (pos % 2 == 0) 0xFF0D1117.toInt() else 0xFF111827.toInt())
            return row
        }
    }
}
