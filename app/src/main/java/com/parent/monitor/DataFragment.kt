package com.parent.monitor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
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
        rvList    = v.findViewById(R.id.rvDataList)

        rvGallery?.layoutManager = GridLayoutManager(context, 3)
        rvList?.layoutManager    = LinearLayoutManager(context)

        val act = requireActivity() as MainActivity

        val categoryBtns = listOf(
            v.findViewById<Button>(R.id.btnDataGallery) to "gallery",
            v.findViewById<Button>(R.id.btnDataCalls)   to "calls",
            v.findViewById<Button>(R.id.btnDataSms)     to "sms",
            v.findViewById<Button>(R.id.btnDataApps)    to "apps"
        )

        fun selectCat(cat: String) {
            currentCategory = cat
            categoryBtns.forEach { (btn, key) ->
                if (key == cat) {
                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt())
                    btn.setTextColor(0xFF000000.toInt())
                } else {
                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
                    btn.setTextColor(0xFFAAAAAA.toInt())
                }
            }
            tvDataStatus?.text = "$cat — tap Fetch to load"
        }

        categoryBtns.forEach { (btn, key) -> btn.setOnClickListener { selectCat(key) } }

        v.findViewById<Button>(R.id.btnDataFetch).setOnClickListener {
            tvDataStatus?.text = "Fetching $currentCategory..."
            when (currentCategory) {
                "gallery" -> act.wsManager?.sendCommandObj(org.json.JSONObject().apply {
                    put("command", "get_gallery"); put("limit", 30) })
                "calls"   -> act.wsManager?.sendCommand("get_call_log")
                "sms"     -> act.wsManager?.sendCommand("get_sms")
                "apps"    -> act.wsManager?.sendCommand("get_app_usage")
            }
        }
        (activity as? MainActivity)?.dataFragment = this
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.dataFragment = null
        tvDataStatus = null; rvGallery = null; rvList = null
    }

    fun onGallery(photos: JSONArray) {
        rvGallery?.visibility = View.VISIBLE
        rvList?.visibility = View.GONE
        val items = (0 until photos.length()).map { photos.getJSONObject(it) }
        rvGallery?.adapter = GalleryAdapter(items)
        tvDataStatus?.text = "Gallery: ${photos.length()} photos"
    }

    fun onList(title: String, items: JSONArray, formatter: (JSONObject) -> String) {
        rvGallery?.visibility = View.GONE
        rvList?.visibility = View.VISIBLE
        val texts = (0 until items.length()).map { formatter(items.getJSONObject(it)) }
        rvList?.adapter = TextListAdapter(texts)
        tvDataStatus?.text = "$title: ${items.length()} items"
    }

    // ── Gallery adapter — proper dp sizing ────────────────────────────
    class GalleryAdapter(private val items: List<JSONObject>) :
        RecyclerView.Adapter<GalleryAdapter.VH>() {

        class VH(val card: CardView) : RecyclerView.ViewHolder(card) {
            val img: ImageView = card.findViewById(android.R.id.icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val density = parent.context.resources.displayMetrics.density
            val sizePx  = (110 * density).toInt()

            val iv = ImageView(parent.context).apply {
                id = android.R.id.icon
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val card = CardView(parent.context).apply {
                radius = 8 * density
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, sizePx
                ).also { it.setMargins((2 * density).toInt(), (2 * density).toInt(),
                    (2 * density).toInt(), (2 * density).toInt()) }
                addView(iv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setCardBackgroundColor(0xFF1A1A2E.toInt())
            }
            return VH(card)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val b64 = items[pos].optString("thumb")
            if (b64.isNotEmpty()) {
                try {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    h.img.setImageBitmap(bmp)
                } catch (_: Exception) {
                    h.img.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                h.img.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
    }

    // ── Text list adapter ──────────────────────────────────────────────
    class TextListAdapter(private val items: List<String>) :
        RecyclerView.Adapter<TextListAdapter.VH>() {

        class VH(val card: CardView) : RecyclerView.ViewHolder(card) {
            val tv: TextView = card.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val density = parent.context.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                id = android.R.id.text1
                setPadding((14*density).toInt(),(12*density).toInt(),(14*density).toInt(),(12*density).toInt())
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val card = CardView(parent.context).apply {
                radius = (8 * density)
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins((4*density).toInt(),(3*density).toInt(),(4*density).toInt(),(3*density).toInt()) }
                addView(tv)
                setCardBackgroundColor(0xFF0E0E24.toInt())
            }
            return VH(card)
        }

        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) { h.tv.text = items[pos] }
    }
}
