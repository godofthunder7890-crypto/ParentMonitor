package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class LimitsFragment : Fragment() {

    private var tvLimitsStatus: TextView? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_limits, c, false)
        val act = requireActivity() as MainActivity
        tvLimitsStatus = v.findViewById(R.id.tvLimitsStatus)

        // Screen Time
        val etLimitPkg  = v.findViewById<EditText>(R.id.etLimitPackage)
        val etLimitMins = v.findViewById<EditText>(R.id.etLimitMinutes)
        v.findViewById<Button>(R.id.btnSetLimit).setOnClickListener {
            val pkg  = etLimitPkg.text.toString().trim()
            val mins = etLimitMins.text.toString().toIntOrNull() ?: 0
            if (pkg.isNotEmpty()) {
                act.wsManager?.sendCommandObj(JSONObject().apply {
                    put("command", "set_time_limit"); put("package", pkg); put("minutes", mins)
                })
                showStatus("⏱ $pkg: ${mins}min/day set")
            }
        }

        // Quick time limits
        v.findViewById<Button>(R.id.btnLimitYoutube30).setOnClickListener {
            sendLimit("com.google.android.youtube", 30, act); showStatus("📺 YouTube: 30min/day") }
        v.findViewById<Button>(R.id.btnLimitGames60).setOnClickListener {
            sendLimit("com.tencent.ig", 60, act); showStatus("🎮 PUBG: 60min/day") }

        // Keyword alerts
        val etKeyword = v.findViewById<EditText>(R.id.etKeyword)
        val keywordListView = v.findViewById<ListView>(R.id.lvKeywords)
        val kwList = mutableListOf<String>()
        val kwAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, kwList)
        keywordListView.adapter = kwAdapter
        keywordListView.setOnItemLongClickListener { _, _, pos, _ ->
            kwList.removeAt(pos); kwAdapter.notifyDataSetChanged()
            pushKeywords(kwList, act); true
        }

        v.findViewById<Button>(R.id.btnAddKeyword).setOnClickListener {
            val kw = etKeyword.text.toString().trim()
            if (kw.isNotEmpty()) {
                kwList.add(kw); kwAdapter.notifyDataSetChanged()
                etKeyword.text.clear()
                pushKeywords(kwList, act)
            }
        }

        // Quick keywords
        v.findViewById<Button>(R.id.btnPresetKeywords).setOnClickListener {
            val presets = listOf("drugs", "fight", "hate", "kill", "bad", "sex", "porn", "dangerous")
            kwList.addAll(presets.filter { !kwList.contains(it) })
            kwAdapter.notifyDataSetChanged()
            pushKeywords(kwList, act)
            showStatus("✅ Safety keywords loaded")
        }

        // Internet Schedule — UI #6: Use TimePickerDialog instead of plain EditText
        val etOffStart = v.findViewById<EditText>(R.id.etOffStart)
        val etOffEnd   = v.findViewById<EditText>(R.id.etOffEnd)
        var scheduleStartHour = 23
        var scheduleEndHour   = 6

        // Override EditText to open TimePicker on click
        etOffStart.isFocusable = false
        etOffStart.isClickable = true
        etOffStart.setText("23:00")
        etOffStart.setOnClickListener {
            android.app.TimePickerDialog(requireContext(), { _, hr, _ ->
                scheduleStartHour = hr
                etOffStart.setText(String.format("%02d:00", hr))
            }, scheduleStartHour, 0, true).show()
        }
        etOffEnd.isFocusable = false
        etOffEnd.isClickable = true
        etOffEnd.setText("06:00")
        etOffEnd.setOnClickListener {
            android.app.TimePickerDialog(requireContext(), { _, hr, _ ->
                scheduleEndHour = hr
                etOffEnd.setText(String.format("%02d:00", hr))
            }, scheduleEndHour, 0, true).show()
        }

        v.findViewById<Button>(R.id.btnSetSchedule).setOnClickListener {
            act.wsManager?.sendCommandObj(JSONObject().apply {
                put("command", "set_schedule")
                put("off_hour_start", scheduleStartHour)
                put("off_hour_end", scheduleEndHour)
            })
            showStatus("🌙 Internet off ${String.format("%02d", scheduleStartHour)}:00 - ${String.format("%02d", scheduleEndHour)}:00")
        }
        v.findViewById<Button>(R.id.btnDisableSchedule).setOnClickListener {
            act.wsManager?.sendCommand("disable_schedule")
            showStatus("✅ Schedule disabled")
        }
        v.findViewById<Button>(R.id.btnNightMode).setOnClickListener {
            act.wsManager?.sendCommandObj(JSONObject().apply {
                put("command", "set_schedule")
                put("off_hour_start", 22); put("off_hour_end", 7)
            })
            showStatus("🌙 Night mode: 10pm - 7am")
        }

        (activity as? MainActivity)?.limitsFragment = this
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.limitsFragment = null
        tvLimitsStatus = null
    }

    fun onScheduleEvent(action: String, hour: Int) {
        tvLimitsStatus?.text = when (action) {
            "internet_off" -> "🚫 Internet auto-off at ${hour}:00"
            "internet_on"  -> "✅ Internet auto-on at ${hour}:00"
            else -> action
        }
    }

    private fun sendLimit(pkg: String, mins: Int, act: MainActivity) {
        act.wsManager?.sendCommandObj(JSONObject().apply {
            put("command", "set_time_limit"); put("package", pkg); put("minutes", mins)
        })
    }

    private fun pushKeywords(list: List<String>, act: MainActivity) {
        act.wsManager?.sendCommandObj(JSONObject().apply {
            put("command", "set_keywords"); put("keywords", JSONArray(list))
        })
        showStatus("🔍 ${list.size} keywords active")
    }

    private fun showStatus(msg: String) {
        tvLimitsStatus?.text = msg
        tvLimitsStatus?.setTextColor(0xFF00E5FF.toInt())
    }
}
