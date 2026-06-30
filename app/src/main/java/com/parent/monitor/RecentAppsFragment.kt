package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class RecentAppsFragment : Fragment() {

    data class RecentApp(
        val pkg:       String,
        val name:      String,
        val installedAt: String,
        val ts:        Long,
        var blocked:   Boolean = false
    )

    private val apps    = mutableListOf<RecentApp>()
    private var adapter: RecentAdapter? = null
    private var tvStatus: TextView? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_recent_apps, c, false)
        val act = requireActivity() as MainActivity

        tvStatus = v.findViewById(R.id.tvRecentStatus)

        v.findViewById<Button>(R.id.btnRefreshRecent).setOnClickListener {
            act.sendCommand("get_recent_installs")
            tvStatus?.text = "Fetching..."
        }

        val lv = v.findViewById<ListView>(R.id.lvRecentApps)
        adapter = RecentAdapter()
        lv.adapter = adapter

        act.sendCommand("get_recent_installs")
        return v
    }

    fun onRecentInstalls(msg: JSONObject) {
        val arr = msg.optJSONArray("apps") ?: return
        val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        apps.clear()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            apps.add(RecentApp(
                pkg         = o.optString("package"),
                name        = o.optString("name", o.optString("package").substringAfterLast('.')),
                installedAt = fmt.format(Date(o.optLong("installed_at", 0L))),
                ts          = o.optLong("installed_at", 0L)
            ))
        }
        apps.sortByDescending { it.ts }
        activity?.runOnUiThread {
            adapter?.notifyDataSetChanged()
            tvStatus?.text = "${apps.size} recently installed apps"
        }
    }

    fun onNewInstall(msg: JSONObject) {
        val pkg  = msg.optString("package")
        val name = msg.optString("name", pkg.substringAfterLast('.'))
        val ts   = msg.optLong("time", System.currentTimeMillis())
        val fmt  = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        apps.add(0, RecentApp(pkg, name, fmt.format(Date(ts)), ts))
        activity?.runOnUiThread {
            adapter?.notifyDataSetChanged()
            tvStatus?.text = "🆕 New app installed: $name"
        }
    }

    inner class RecentAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(pos: Int, conv: View?, parent: ViewGroup): View {
            val row = conv ?: LayoutInflater.from(context)
                .inflate(R.layout.item_recent_app_row, parent, false)
            val a = apps[pos]
            val act = requireActivity() as MainActivity

            row.findViewById<TextView>(R.id.tvRecentAppName).text = a.name
            row.findViewById<TextView>(R.id.tvRecentAppPkg).text  = "${a.pkg} • ${a.installedAt}"

            val btn = row.findViewById<Button>(R.id.btnRecentBlock)
            btn.text = if (a.blocked) "✅ Blocked" else "🚫 Block"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (a.blocked) 0xFF2E7D32.toInt() else 0xFFB71C1C.toInt()
            )
            btn.setOnClickListener {
                a.blocked = !a.blocked
                if (a.blocked) {
                    act.sendCommandObj(JSONObject().apply {
                        put("command", "set_blocked_apps")
                        val arr = JSONArray().put(a.pkg)
                        put("apps", arr)
                    })
                }
                notifyDataSetChanged()
            }
            row.setBackgroundColor(if (pos % 2 == 0) 0xFF0D1117.toInt() else 0xFF111827.toInt())
            return row
        }
    }
}
