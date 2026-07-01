package com.parent.monitor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class AlbumsSafetyFragment : Fragment() {

    private var tvStatus: TextView? = null
    private var llItems: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var flaggedItems = JSONArray()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val act = requireActivity() as MainActivity

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF060612.toInt())
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0A0A1E.toInt())
            setPadding(28, 20, 28, 20)
        }
        val tvTitle = TextView(ctx).apply {
            text = "ALBUMS SAFETY"; textSize = 13f
            setTextColor(0xFF00E5FF.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        tvStatus = TextView(ctx).apply {
            text = "Not scanned"; textSize = 12f; setTextColor(0xFF888888.toInt())
        }
        header.addView(tvTitle); header.addView(tvStatus)
        root.addView(header)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(16, 14, 16, 8)
        }
        val btnScan = Button(ctx).apply {
            text = "🔍 SCAN GALLERY"; textSize = 13f
            setTextColor(0xFF000000.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setBackgroundColor(0xFF00E5FF.toInt())
        }
        btnScan.setOnClickListener {
            tvStatus?.text = "Scanning..."
            act.sendCommand("scan_albums")
        }
        btnRow.addView(btnScan)
        root.addView(btnRow)

        val tvInfo = TextView(ctx).apply {
            text = "Scans for images with suspicious filenames, hidden folders, or large files from unknown sources."
            textSize = 11f; setTextColor(0xFF555577.toInt()); setPadding(28, 4, 28, 12)
        }
        root.addView(tvInfo)

        scroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0).apply { weight = 1f }
        }
        llItems = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(12, 0, 12, 24)
        }
        scroll!!.addView(llItems)
        root.addView(scroll)

        (activity as? MainActivity)?.albumsSafetyFragment = this
        return root
    }

    fun onScanResult(msg: JSONObject) {
        val items = msg.optJSONArray("items") ?: JSONArray()
        flaggedItems = items
        activity?.runOnUiThread {
            // FIX: use context (nullable) not requireContext() — fragment may be detached
            // by the time the runOnUiThread block executes
            val ctx = context ?: return@runOnUiThread
            val count = items.length()
            tvStatus?.text = "$count flagged"
            tvStatus?.setTextColor(if (count > 0) 0xFFFF5722.toInt() else 0xFF00C853.toInt())
            llItems?.removeAllViews()
            if (count == 0) {
                val tv = TextView(ctx).apply {
                    text = "✅ No suspicious images found."; textSize = 14f
                    setTextColor(0xFF00C853.toInt()); gravity = android.view.Gravity.CENTER
                    setPadding(0, 60, 0, 0)
                }
                llItems?.addView(tv)
                return@runOnUiThread
            }
            for (k in 0 until items.length()) {
                val item = items.optJSONObject(k) ?: continue
                addItemCard(ctx, item)
            }
        }
    }

    private fun addItemCard(ctx: android.content.Context, item: JSONObject) {
        val act = activity as? MainActivity ?: return
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D2B.toInt())
            setPadding(16, 12, 16, 12)
            val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 10) }
            layoutParams = lp
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val thumbB64 = item.optString("thumb", "")
        val ivThumb = ImageView(ctx).apply {
            val lp = LinearLayout.LayoutParams(80.dp(ctx), 80.dp(ctx))
            lp.setMargins(0, 0, 12, 0); layoutParams = lp
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF1A1A3E.toInt())
        }
        if (thumbB64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(thumbB64, Base64.DEFAULT)
                val _optsbmp = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, _optsbmp)
                    val _scbmp = maxOf(1, maxOf(_optsbmp.outWidth, _optsbmp.outHeight) / 300)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = _scbmp })
                if (bmp != null) ivThumb.setImageBitmap(bmp)
            } catch (_: Exception) {}
        }
        topRow.addView(ivThumb)

        val infoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val name    = item.optString("name", "Unknown")
        val bucket  = item.optString("bucket", "")
        val sizeKb  = item.optLong("size_kb")
        val reasons = item.optJSONArray("reasons")
        val reasonStr = if (reasons != null) (0 until reasons.length()).joinToString(", ") { reasons.getString(it) } else ""

        infoCol.addView(TextView(ctx).apply {
            text = name; textSize = 13f; setTextColor(0xFFFFFFFF.toInt())
        })
        infoCol.addView(TextView(ctx).apply {
            text = bucket; textSize = 11f; setTextColor(0xFF888888.toInt()); setPadding(0, 2, 0, 2)
        })
        infoCol.addView(TextView(ctx).apply {
            text = "${sizeKb}KB  •  ⚠ $reasonStr"; textSize = 11f; setTextColor(0xFFFF9800.toInt())
        })
        topRow.addView(infoCol)

        val btnView = Button(ctx).apply {
            text = "VIEW"; textSize = 10f; setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00E5FF.toInt())
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(8, 0, 0, 0); layoutParams = lp
        }
        btnView.setOnClickListener {
            val uriStr = item.optString("uri")
            if (uriStr.isNotEmpty()) {
                act.sendCommandObj(org.json.JSONObject().apply {
                    put("command", "get_album_image"); put("uri", uriStr)
                })
            }
        }
        topRow.addView(btnView)
        card.addView(topRow)
        llItems?.addView(card)
    }

    fun onAlbumFullImage(msg: JSONObject) {
        val b64 = msg.optString("data", "")
        if (b64.isEmpty()) return
        activity?.runOnUiThread {
            // FIX: use context (nullable) — fragment may be detached when runOnUiThread fires
            val ctx = context ?: return@runOnUiThread
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val _optsbmp = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, _optsbmp)
                    val _scbmp = maxOf(1, maxOf(_optsbmp.outWidth, _optsbmp.outHeight) / 300)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = _scbmp }) ?: return@runOnUiThread
                val iv = ImageView(ctx).apply {
                    setImageBitmap(bmp); adjustViewBounds = true; setBackgroundColor(0xFF000000.toInt())
                }
                android.app.AlertDialog.Builder(ctx)
                    .setView(iv).setNegativeButton("Close", null).show()
            } catch (_: Exception) {}
        }
    }

    private fun Int.dp(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()

    override fun onDestroyView() { (activity as? MainActivity)?.albumsSafetyFragment = null; super.onDestroyView() }
}
