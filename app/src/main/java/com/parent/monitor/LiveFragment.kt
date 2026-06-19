package com.parent.monitor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

class LiveFragment : Fragment() {

    private lateinit var btnTabScreen: Button
    private lateinit var btnTabCamera: Button
    private lateinit var btnTabMic: Button
    private lateinit var panelScreen: LinearLayout
    private lateinit var panelCamera: LinearLayout
    private lateinit var panelMic: LinearLayout
    private lateinit var imgScreen: ImageView
    private lateinit var imgCamera: ImageView
    private lateinit var dotScreen: View
    private lateinit var dotCamera: View
    private lateinit var dotMic: View
    private lateinit var tvScreenInfo: TextView
    private lateinit var tvCameraInfo: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var tvScreenFps: TextView
    private lateinit var tvCameraFps: TextView
    private lateinit var tvScreenInterval: TextView
    private lateinit var btnToggleScreen: Button
    private lateinit var btnToggleCamera: Button
    private lateinit var btnToggleMic: Button
    private lateinit var btnScreenFaster: Button
    private lateinit var btnScreenSlower: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    // BUG FIX: Thread() per frame bad tha — 30fps pe 30 threads/sec ban-bigar rahe the.
    // Single thread executor se ek hi background thread reuse hota hai.
    private val decodeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var screenStreaming = false
    private var cameraStreaming = false
    private var micStreaming    = false
    private var screenInterval = 1000L
    private var cameraInterval = 33L  // 30fps default

    // FPS tracking
    private var screenFrameCount = 0L
    private var cameraFrameCount = 0L
    private var screenLastFps = System.currentTimeMillis()
    private var cameraLastFps = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_live, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupTabs()
        setupControls()
        // Receive frames from MainActivity
        (activity as? MainActivity)?.liveFragment = this
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.liveFragment = null
        decodeExecutor.shutdown()
        super.onDestroyView()
    }

    private fun bindViews(v: View) {
        btnTabScreen = v.findViewById(R.id.btnTabScreen)
        btnTabCamera = v.findViewById(R.id.btnTabCamera)
        btnTabMic    = v.findViewById(R.id.btnTabMic)
        panelScreen  = v.findViewById(R.id.panelScreen)
        panelCamera  = v.findViewById(R.id.panelCamera)
        panelMic     = v.findViewById(R.id.panelMic)
        imgScreen    = v.findViewById(R.id.imgScreen)
        imgCamera    = v.findViewById(R.id.imgCamera)
        dotScreen    = v.findViewById(R.id.dotScreen)
        dotCamera    = v.findViewById(R.id.dotCamera)
        dotMic       = v.findViewById(R.id.dotMic)
        tvScreenInfo = v.findViewById(R.id.tvScreenInfo)
        tvCameraInfo = v.findViewById(R.id.tvCameraInfo)
        tvMicStatus  = v.findViewById(R.id.tvMicStatus)
        tvScreenFps  = v.findViewById(R.id.tvScreenFps)
        tvCameraFps  = v.findViewById(R.id.tvCameraFps)
        tvScreenInterval = v.findViewById(R.id.tvScreenInterval)
        btnToggleScreen  = v.findViewById(R.id.btnToggleScreen)
        btnToggleCamera  = v.findViewById(R.id.btnToggleCamera)
        btnToggleMic     = v.findViewById(R.id.btnToggleMic)
        btnScreenFaster  = v.findViewById(R.id.btnScreenFaster)
        btnScreenSlower  = v.findViewById(R.id.btnScreenSlower)
    }

    private fun setupTabs() {
        btnTabScreen.setOnClickListener { showPanel("screen") }
        btnTabCamera.setOnClickListener { showPanel("camera") }
        btnTabMic.setOnClickListener    { showPanel("mic") }
        showPanel("screen")
    }

    private fun showPanel(tab: String) {
        panelScreen.visibility = if (tab == "screen") View.VISIBLE else View.GONE
        panelCamera.visibility = if (tab == "camera") View.VISIBLE else View.GONE
        panelMic.visibility    = if (tab == "mic")    View.VISIBLE else View.GONE

        val cyan  = 0xFF00E5FF.toInt()
        val dim   = 0xFF1A1A2E.toInt()
        val black = 0xFF000000.toInt()
        btnTabScreen.setBackgroundColor(if (tab == "screen") cyan  else dim)
        btnTabCamera.setBackgroundColor(if (tab == "camera") 0xFFAA00FF.toInt() else dim)
        btnTabMic.setBackgroundColor(if (tab == "mic") 0xFF00C853.toInt() else dim)
        btnTabScreen.setTextColor(if (tab == "screen") black else 0xFFAAAAAA.toInt())
        btnTabCamera.setTextColor(if (tab == "camera") black else 0xFFAAAAAA.toInt())
        btnTabMic.setTextColor(if (tab == "mic") black else 0xFFAAAAAA.toInt())
    }

    private fun setupControls() {
        val intervals = listOf(16L, 33L, 50L, 100L, 200L, 500L, 1000L)  // 60fps→30fps→20fps→10fps→5fps→2fps→1fps
        var idx = 1  // default 30fps (33ms)

        fun updateIntervalLabel() {
            screenInterval = intervals[idx]
            val fps = (1000.0 / screenInterval).toInt(); tvScreenInterval.text = "${fps}fps (${screenInterval}ms)"
        }

        btnScreenFaster.setOnClickListener {
            if (idx > 0) { idx--; updateIntervalLabel() }
            if (screenStreaming) sendCommand(JSONObject().apply { put("command", "start_screen_stream"); put("interval", screenInterval) })
        }
        btnScreenSlower.setOnClickListener {
            if (idx < intervals.lastIndex) { idx++; updateIntervalLabel() }
            if (screenStreaming) sendCommand(JSONObject().apply { put("command", "start_screen_stream"); put("interval", screenInterval) })
        }
        updateIntervalLabel()

        btnToggleScreen.setOnClickListener {
            screenStreaming = !screenStreaming
            if (screenStreaming) {
                // Step 1: Request screen capture permission on child device
                sendCommand(JSONObject().apply { put("command", "request_screen_permission") })
                btnToggleScreen.text = "REQUESTING..."
                btnToggleScreen.setBackgroundColor(0xFFFF9800.toInt())
                tvScreenInfo.text = "Asking child for permission..."
                setDotColor(dotScreen, true)
                // Step 2: Start stream after 3s (user has time to allow dialog on child)
                mainHandler.postDelayed({
                    if (screenStreaming) {
                        sendCommand(JSONObject().apply { put("command", "start_screen_stream"); put("interval", screenInterval) })
                        btnToggleScreen.text = "STOP SCREEN"
                        btnToggleScreen.setBackgroundColor(0xFFFF1744.toInt())
                        tvScreenInfo.text = "Streaming..."
                    }
                }, 3000)
            } else {
                sendCommand(JSONObject().apply { put("command", "stop_screen_stream") })
                btnToggleScreen.text = "START SCREEN"
                btnToggleScreen.setBackgroundColor(0xFF00E5FF.toInt())
                tvScreenInfo.text = "Stopped"
                tvScreenFps.text = ""
                setDotColor(dotScreen, false)
            }
        }

        btnToggleCamera.setOnClickListener {
            cameraStreaming = !cameraStreaming
            if (cameraStreaming) {
                sendCommand(JSONObject().apply { put("command", "start_camera_stream"); put("interval", cameraInterval) })
                btnToggleCamera.text = "STOP CAMERA"
                btnToggleCamera.setBackgroundColor(0xFFFF1744.toInt())
                tvCameraInfo.text = "Streaming..."
                setDotColor(dotCamera, true)
            } else {
                sendCommand(JSONObject().apply { put("command", "stop_camera_stream") })
                btnToggleCamera.text = "START CAMERA"
                btnToggleCamera.setBackgroundColor(0xFFAA00FF.toInt())
                tvCameraInfo.text = "Stopped"
                tvCameraFps.text = ""
                setDotColor(dotCamera, false)
            }
        }

        btnToggleMic.setOnClickListener {
            micStreaming = !micStreaming
            if (micStreaming) {
                sendCommand(JSONObject().apply { put("command", "start_mic_stream") })
                btnToggleMic.text = "STOP LISTENING"
                btnToggleMic.setBackgroundColor(0xFFFF1744.toInt())
                tvMicStatus.text = "Mic On"
                tvMicStatus.setTextColor(0xFF00C853.toInt())
                setDotColor(dotMic, true)
            } else {
                sendCommand(JSONObject().apply { put("command", "stop_mic_stream") })
                btnToggleMic.text = "START LISTENING"
                btnToggleMic.setBackgroundColor(0xFF00C853.toInt())
                tvMicStatus.text = "Mic Off"
                tvMicStatus.setTextColor(0xFF555566.toInt())
                setDotColor(dotMic, false)
            }
        }
    }

    /** Called by MainActivity when a screen/camera frame arrives */
    fun onScreenFrame(b64: String) {
        decodeExecutor.execute {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                screenFrameCount++
                val now = System.currentTimeMillis()
                val elapsed = now - screenLastFps
                val fps = if (elapsed >= 1000) {
                    val f = screenFrameCount * 1000 / elapsed
                    screenFrameCount = 0
                    screenLastFps = now
                    f
                } else {
                    screenFrameCount * 1000 / (elapsed + 1)
                }
                mainHandler.post {
                    try {
                        imgScreen.setImageBitmap(bmp)
                        tvScreenFps.text = "${fps}fps"
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun onCameraFrame(b64: String) {
        decodeExecutor.execute {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                cameraFrameCount++
                val now = System.currentTimeMillis()
                val elapsed = now - cameraLastFps
                val fps = if (elapsed >= 1000) {
                    val f = cameraFrameCount * 1000 / elapsed
                    cameraFrameCount = 0
                    cameraLastFps = now
                    f
                } else {
                    cameraFrameCount * 1000 / (elapsed + 1)
                }
                mainHandler.post {
                    try {
                        imgCamera.setImageBitmap(bmp)
                        tvCameraFps.text = "${fps}fps"
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun setDotColor(dot: View, on: Boolean) {
        dot.setBackgroundColor(if (on) 0xFF00C853.toInt() else 0xFFFF1744.toInt())
    }

    private fun sendCommand(cmd: JSONObject) {
        (activity as? MainActivity)?.sendToChild(cmd)
    }
}
