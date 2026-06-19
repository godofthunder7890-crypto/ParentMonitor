package com.parent.monitor

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.fragment.app.Fragment

class LocationFragment : Fragment() {

    private var webView: WebView? = null
    private var tvCoords: TextView? = null
    private var tvAccuracy: TextView? = null
    private var llHistory: LinearLayout? = null
    private val locationHistory = mutableListOf<String>()
    private var lastLat = 0.0
    private var lastLng = 0.0

    @SuppressLint("SetJavaScriptEnabled")
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
            text = "LIVE LOCATION (OpenStreetMap)"; textSize = 12f
            setTextColor(0xFF00E5FF.toInt()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val btnFetch = Button(ctx).apply {
            text = "📍"; textSize = 14f; setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00E5FF.toInt()); setPadding(20, 8, 20, 8)
        }
        btnFetch.setOnClickListener { act.sendCommand("get_location") }
        header.addView(tvTitle); header.addView(btnFetch)
        root.addView(header)

        tvCoords = TextView(ctx).apply {
            text = "Waiting for location…"; textSize = 14f
            setTextColor(0xFF00E5FF.toInt()); setPadding(28, 10, 28, 0)
        }
        root.addView(tvCoords)

        tvAccuracy = TextView(ctx).apply {
            text = ""; textSize = 11f; setTextColor(0xFF555577.toInt()); setPadding(28, 2, 28, 6)
        }
        root.addView(tvAccuracy)

        webView = WebView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0).apply { weight = 1f }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, r: WebResourceRequest?, e: WebResourceError?) {}
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            }
            loadData(buildMapHtml(20.5937, 78.9629, false), "text/html", "UTF-8")
        }
        root.addView(webView)

        val histHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0A0A1E.toInt())
            setPadding(28, 8, 28, 8)
        }
        histHeader.addView(TextView(ctx).apply {
            text = "History"; textSize = 11f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        val btnClear = Button(ctx).apply {
            text = "Clear"; textSize = 10f; setTextColor(0xFF888888.toInt())
            setBackgroundColor(0); setPadding(16, 4, 0, 4)
        }
        btnClear.setOnClickListener { locationHistory.clear(); llHistory?.removeAllViews() }
        histHeader.addView(btnClear)
        root.addView(histHeader)

        val scrollHist = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 120.dp(ctx))
        }
        llHistory = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(28, 4, 28, 8)
        }
        scrollHist.addView(llHistory)
        root.addView(scrollHist)

        (activity as? MainActivity)?.locationFragment = this
        return root
    }

    fun onLocation(lat: Double, lng: Double, acc: Float) {
        lastLat = lat; lastLng = lng
        activity?.runOnUiThread {
            val ls = "%.6f".format(lat)
            val ln = "%.6f".format(lng)
            tvCoords?.text = "📍 $ls, $ln"
            tvAccuracy?.text = if (acc > 0) "Accuracy: ±${acc.toInt()}m" else ""

            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val entry = "$time — $ls, $ln"
            if (locationHistory.firstOrNull() != entry) {
                locationHistory.add(0, entry)
                if (locationHistory.size > 25) locationHistory.removeAt(locationHistory.size - 1)
                val tv = TextView(requireContext()).apply {
                    text = entry; textSize = 10f; setTextColor(0xFF555577.toInt())
                    setPadding(0, 2, 0, 2)
                }
                llHistory?.addView(tv, 0)
                if ((llHistory?.childCount ?: 0) > 25) llHistory?.removeViewAt(25)
            }

            webView?.evaluateJavascript("updateLocation($lat, $lng);", null)
        }
    }

    private fun buildMapHtml(initLat: Double, initLng: Double, @Suppress("UNUSED_PARAMETER") hasLoc: Boolean): String {
        return """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>*{margin:0;padding:0;box-sizing:border-box}html,body,#map{width:100%;height:100%;background:#060612}</style>
</head><body><div id="map"></div><script>
var map=L.map('map',{zoomControl:true,attributionControl:true}).setView([$initLat,$initLng],4);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);
var marker=null,circle=null;
function updateLocation(lat,lng){
  if(marker===null){
    marker=L.circleMarker([lat,lng],{color:'#00E5FF',fillColor:'#00E5FF',fillOpacity:0.9,radius:10,weight:3}).addTo(map);
    marker.bindPopup('<b>Child</b><br>'+lat.toFixed(6)+', '+lng.toFixed(6));
    circle=L.circle([lat,lng],{color:'#00E5FF44',fillColor:'#00E5FF22',radius:80}).addTo(map);
    map.setView([lat,lng],16);
  } else {
    marker.setLatLng([lat,lng]);
    marker.setPopupContent('<b>Child</b><br>'+lat.toFixed(6)+', '+lng.toFixed(6));
    circle.setLatLng([lat,lng]);
    map.panTo([lat,lng]);
  }
}
</script></body></html>"""
    }

    private fun Int.dp(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()

    override fun onResume()  { super.onResume();  webView?.onResume()  }
    override fun onPause()   { super.onPause();   webView?.onPause()   }
    override fun onDestroy() { webView?.destroy(); webView = null; super.onDestroy() }
    override fun onDestroyView() {
        (activity as? MainActivity)?.locationFragment = null
        super.onDestroyView()
    }
}
