package com.parent.monitor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class DataFragment : Fragment() {

    private var currentCategory = "gallery"
    private var tvDataStatus: TextView? = null
    private var rvGallery: RecyclerView? = null
    private var rvList: RecyclerView? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_data, c, false)
        tvDataStatus = v.findViewById(R.id.tvDataStatus)
        rvGallery = v.findViewById(R.id.rvDataGallery)
        rvList = v.findViewById(R.id.rvDataList)

        rvGallery?.layoutManager = GridLayoutManager(context, 3)
        rvList?.layoutManager = LinearLayoutManager(context)

        val act = requireActivity() as MainActivity

        fun selectCat(cat: String, btn: Button) {
            currentCategory = cat
            listOf<Int>(R.id.btnDataGallery, R.id.btnDataCalls, R.id.btnDataSms, R.id.btnDataApps)
                .forEach { id ->
                    v.findViewById<Button>(id).backgroundTintList =
                        android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
                    v.findViewById<Button>(id).setTextColor(0xFFFFFFFF.toInt())
                }
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00D4FF.toInt())
            btn.setTextColor(0xFF000000.toInt())
            tvDataStatus?.text = "$cat — tap Fetch to load"
        }

        v.findViewById<Button>(R.id.btnDataGallery).setOnClickListener { selectCat("gallery", it as Button) }
        v.findViewById<Button>(R.id.btnDataCalls).setOnClickListener   { selectCat("calls", it as Button) }
        v.findViewById<Button>(R.id.btnDataSms).setOnClickListener     { selectCat("sms", it as Button) }
        v.findViewById<Button>(R.id.btnDataApps).setOnClickListener    { selectCat("apps", it as Button) }

        v.findViewById<Button>(R.id.btnDataFetch).setOnClickListener {
            tvDataStatus?.text = "Fetching $currentCategory..."
            when (currentCategory) {
                "gallery" -> act.wsManager?.sendCommand("get_gallery")
                "calls"   -> act.wsManager?.sendCommand("get_call_log")
                "sms"     -> act.wsManager?.sendCommand("get_sms")
                "apps"    -> act.wsManager?.sendCommand("get_app_usage")
            }
        }
        return v
    }

    fun onGallery(photos: JSONArray) {
        rvGallery?.visibility = View.VISIBLE
        rvList?.visibility = View.GONE
        val items = (0 until photos.length()).map { photos.getJSONObject(it) }
        rvGallery?.adapter = GalleryGridAdapter(items)
        tvDataStatus?.text = "Gallery: ${photos.length()} photos"
    }

    fun onList(title: String, items: JSONArray, formatter: (JSONObject) -> String) {
        rvGallery?.visibility = View.GONE
        rvList?.visibility = View.VISIBLE
        val texts = (0 until items.length()).map { formatter(items.getJSONObject(it)) }
        rvList?.adapter = TextListAdapter(texts)
        tvDataStatus?.text = "$title: ${items.length()} items"
    }

    // ── Adapters ──────────────────────────────────────────────────────────────
    class GalleryGridAdapter(private val items: List<JSONObject>) :
        RecyclerView.Adapter<GalleryGridAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) { val img: ImageView = v.findViewById(android.R.id.icon) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val iv = ImageView(p.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110)
                scaleType = ImageView.ScaleType.CENTER_CROP
                id = android.R.id.icon
                setPadding(2,2,2,2)
            }
            return VH(iv)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            try {
                val b64 = items[pos].optString("thumb")
                if (b64.isNotEmpty()) {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    h.img.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                } else {
                    h.img.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } catch (_: Exception) {}
        }
    }

    class TextListAdapter(private val items: List<String>) :
        RecyclerView.Adapter<TextListAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) { val tv: TextView = v as TextView }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val tv = TextView(p.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(16, 12, 16, 12)
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
            }
            return VH(tv)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) { h.tv.text = items[pos] }
    }
}
