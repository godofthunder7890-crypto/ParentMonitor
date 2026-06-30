package com.parent.monitor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SnapshotFragment : Fragment() {

    data class SnapItem(
        val b64: String,
        val datetime: String,
        val filename: String,
        val ts: Long
    )

    private val snapshots = mutableListOf<SnapItem>()
    private var adapter: SnapAdapter? = null

    private var tvStatus: TextView? = null
    private var tvConfig: TextView? = null
    private var etInterval: EditText? = null
    private var swEnabled: Switch? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_snapshot, c, false)
        val act = requireActivity() as MainActivity

        tvStatus   = v.findViewById(R.id.tvSnapStatus)
        tvConfig   = v.findViewById(R.id.tvSnapConfig)
        etInterval = v.findViewById(R.id.etSnapInterval)
        swEnabled  = v.findViewById(R.id.swSnapEnabled)

        val lv = v.findViewById<ListView>(R.id.lvSnapshots)
        adapter = SnapAdapter()
        lv.adapter = adapter

        v.findViewById<Button>(R.id.btnSnapApply).setOnClickListener {
            val on   = swEnabled?.isChecked ?: false
            val mins = etInterval?.text.toString().toIntOrNull() ?: 5
            act.sendCommandObj(JSONObject().apply {
                put("command",          "snapshot_config")
                put("enabled",          on)
                put("interval_minutes", mins)
            })
            tvConfig?.text = if (on) "📸 Snapshots: every ${mins}min" else "📸 Snapshots: OFF"
            tvStatus?.text = "Config applied"
        }

        v.findViewById<Button>(R.id.btnSnapNow).setOnClickListener {
            act.sendCommand("snapshot_now")
            tvStatus?.text = "📸 Capturing..."
        }

        v.findViewById<Button>(R.id.btnSnapClear).setOnClickListener {
            snapshots.clear()
            adapter?.notifyDataSetChanged()
            tvStatus?.text = "Cleared"
        }

        return v
    }

    fun onSnapshot(msg: JSONObject) {
        val b64  = msg.optString("image")
        val dt   = msg.optString("datetime", "—")
        val fn   = msg.optString("filename",  "snap.jpg")
        val ts   = msg.optLong("time", System.currentTimeMillis())
        if (b64.isNotEmpty()) {
            snapshots.add(0, SnapItem(b64, dt, fn, ts))
            if (snapshots.size > 50) snapshots.removeAt(snapshots.size - 1)
            activity?.runOnUiThread {
                adapter?.notifyDataSetChanged()
                tvStatus?.text = "📸 New snapshot: $dt"
            }
        }
    }

    inner class SnapAdapter : BaseAdapter() {
        override fun getCount() = snapshots.size
        override fun getItem(pos: Int) = snapshots[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, conv: View?, parent: ViewGroup): View {
            val row = conv ?: LayoutInflater.from(context).inflate(R.layout.item_snapshot_row, parent, false)
            val item = snapshots[pos]
            row.findViewById<TextView>(R.id.tvSnapTime).text   = item.datetime
            row.findViewById<TextView>(R.id.tvSnapFile).text   = item.filename
            val iv = row.findViewById<ImageView>(R.id.ivSnapThumb)
            try {
                val bytes = Base64.decode(item.b64, Base64.DEFAULT)
                val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                iv.setImageBitmap(bmp)
            } catch (_: Exception) { iv.setImageResource(android.R.drawable.ic_menu_camera) }
            row.setOnClickListener { showFull(item) }
            return row
        }
    }

    private fun showFull(item: SnapItem) {
        try {
            val bytes = Base64.decode(item.b64, Base64.DEFAULT)
            val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val iv    = ImageView(requireContext()).apply { setImageBitmap(bmp) }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(item.datetime)
                .setView(iv)
                .setNegativeButton("Close", null)
                .show()
        } catch (_: Exception) {}
    }
}
