package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class ProtectFragment : Fragment() {

    private var etBlockApp: EditText? = null
    private var lvBlockedApps: ListView? = null
    private var tvProtectStatus: TextView? = null
    private val blockedList = mutableListOf<String>()
    private var listAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_protect, c, false)
        val act = requireActivity() as MainActivity

        etBlockApp    = v.findViewById(R.id.etBlockApp)
        lvBlockedApps = v.findViewById(R.id.lvBlockedApps)
        tvProtectStatus = v.findViewById(R.id.tvProtectStatus)

        listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, blockedList)
        lvBlockedApps?.adapter = listAdapter
        lvBlockedApps?.setOnItemLongClickListener { _, _, pos, _ ->
            blockedList.removeAt(pos)
            listAdapter?.notifyDataSetChanged()
            pushBlockedList()
            true
        }

        v.findViewById<Button>(R.id.btnAddBlock).setOnClickListener {
            val pkg = etBlockApp?.text.toString().trim()
            if (pkg.isNotEmpty() && !blockedList.contains(pkg)) {
                blockedList.add(pkg)
                listAdapter?.notifyDataSetChanged()
                etBlockApp?.text?.clear()
                pushBlockedList()
            }
        }

        v.findViewById<Button>(R.id.btnBlockYoutube).setOnClickListener {
            addBlock("com.google.android.youtube", act)
        }
        v.findViewById<Button>(R.id.btnBlockPubg).setOnClickListener {
            addBlock("com.tencent.ig", act)
        }
        v.findViewById<Button>(R.id.btnBlockInstagram).setOnClickListener {
            addBlock("com.instagram.android", act)
        }
        v.findViewById<Button>(R.id.btnBlockTiktok).setOnClickListener {
            addBlock("com.zhiliaoapp.musically", act)
        }

        v.findViewById<Button>(R.id.btnClearBlocks).setOnClickListener {
            blockedList.clear()
            listAdapter?.notifyDataSetChanged()
            pushBlockedList()
        }

        v.findViewById<Button>(R.id.btnEmergencyLock).setOnClickListener {
            act.wsManager?.sendCommand("emergency_lock")
            tvProtectStatus?.text = "🔴 Emergency lock sent!"
            tvProtectStatus?.setTextColor(0xFFFF4444.toInt())
        }

        v.findViewById<Button>(R.id.btnBlockUnknown)?.setOnClickListener {
            // Push command to block calls from non-contacts
            act.wsManager?.sendCommandObj(JSONObject().apply {
                put("command", "block_contact"); put("number", "UNKNOWN")
            })
            tvProtectStatus?.text = "✅ Unknown callers blocked"
        }

        return v
    }

    fun onDeviceInfo(data: JSONObject) {
        val arr = data.optJSONArray("blocked_apps") ?: return
        blockedList.clear()
        for (i in 0 until arr.length()) blockedList.add(arr.getString(i))
        listAdapter?.notifyDataSetChanged()
    }

    fun onAppBlocked(pkg: String, reason: String) {
        tvProtectStatus?.text = "🚫 Blocked: ${pkg.substringAfterLast('.')} ($reason)"
        tvProtectStatus?.setTextColor(0xFFFF9800.toInt())
    }

    private fun addBlock(pkg: String, act: MainActivity) {
        if (!blockedList.contains(pkg)) {
            blockedList.add(pkg)
            listAdapter?.notifyDataSetChanged()
            pushBlockedList()
        }
    }

    private fun pushBlockedList() {
        val act = activity as? MainActivity ?: return
        val arr = JSONArray(blockedList)
        act.wsManager?.sendCommandObj(JSONObject().apply {
            put("command", "set_blocked_apps")
            put("apps", arr)
        })
        tvProtectStatus?.text = "✅ ${blockedList.size} apps blocked"
        tvProtectStatus?.setTextColor(0xFF4CAF50.toInt())
    }
}
