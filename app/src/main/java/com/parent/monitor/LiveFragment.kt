package com.parent.monitor

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class LiveFragment : Fragment() {

    private var imgScreen: android.widget.ImageView? = null
    private var imgCamera: android.widget.ImageView? = null
    private var tvScreenInfo: TextView? = null
    private var tvCameraInfo: TextView? = null
    private var tvScreenFps: TextView? = null
    private var tvCameraFps: TextView? = null
    private var btnToggleScreen: Button? = null
    private var btnToggleCamera: Button? = null
    private var btnToggleMic: Button? = null
    private var dotScreen: View? = null
    private var dotCamera: View? = null
    private var dotMic: View? = null
    private var tvMicStatus: TextView? = null
    private var tvScreenInterval: TextView? = null
    private var btnScreenFaster: Button? = null
    private var btnScreenSlower: Button? = null

    private var panelScreen: View? = null
    private var panelCamera: View? = null
    private var panelMic: View? = null

    private var screenRunning = false
    private var cameraRunning = false
    private var micRunning = false

    // Bitmap recycling — prevent OOM crashes
    private var lastScreenBitmap: Bitmap? = null
    private var lastCameraBitmap: Bitmap? = null

    // FPS tracking
    private var screenFrameCount = 0
    private var cameraFrameCount = 0
    private var screenFps = 0
    private var cameraFps = 0
    private val fpsHandler = Handler(Looper.getMainLooper())
    private val fpsRunnable = object : Runnable {
        override fun run() {
            screenFps = screenFrameCount; screenFrameCount = 0
            cameraFps = cameraFrameCount; cameraFrameCount = 0
            tvScreenFps?.text = "$screenFps fps"
            tvCameraFps?.text = "$cameraFps fps"
            fpsHandler.postDelayed(this, 1000)
        }
    }

    // Screen interval control (ms)
    private var screenIntervalMs = 1000L
    private val intervalOptions = longArrayOf(500, 1000, 2000, 3000, 5000)
    private var intervalIdx = 1

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_live, c, false)

        imgScreen      = v.findViewById(R.id.imgScreen)
        imgCamera      = v.findViewById(R.id.imgCamera)
        tvScreenInfo   = v.findViewById(R.id.tvScreenInfo)
        tvCameraInfo   = v.findViewById(R.id.tvCameraInfo)
        tvScreenFps    = v.findViewById(R.id.tvScreenFps)
        tvCameraFps    = v.findViewById(R.id.tvCameraFps)
        btnToggleScreen= v.findViewById(R.id.btnToggleScreen)
        btnToggleCamera= v.findViewById(R.id.btnToggleCamera)
        btnToggleMic   = v.findViewById(R.id.btnToggleMic)
        dotScreen      = v.findViewById(R.id.dotScreen)
        dotCamera      = v.findViewById(R.id.dotCamera)
        dotMic         = v.findViewById(R.id.dotMic)
        tvMicStatus    = v.findViewById(R.id.tvMicStatus)
        tvScreenInterval = v.findViewById(R.id.tvScreenInterval)
        btnScreenFaster  = v.findViewById(R.id.btnScreenFaster)
        btnScreenSlower  = v.findViewById(R.id.btnScreenSlower)

        panelScreen = v.findViewById(R.id.panelScreen)
        panelCamera = v.findViewById(R.id.panelCamera)
        panelMic    = v.findViewById(R.id.panelMic)

        setupTabs(v)
        setupControls()
        fpsHandler.post(fpsRunnable)
        return v
    }

    private fun setupTabs(v: View) {
        val btnTabScreen = v.findViewById<Button>(R.id.btnTabScreen)
        val btnTabCamera = v.findViewById<Button>(R.id.btnTabCamera)
        val btnTabMic    = v.findViewById<Button>(R.id.btnTabMic)

        fun selectTab(selected: Int) {
            val tabs = listOf(btnTabScreen, btnTabCamera, btnTabMic)
            val panels = listOf(panelScreen, panelCamera, panelMic)
            val colors = listOf(0xFF00E5FF.toInt(), 0xFF6A1B9A.toInt(), 0xFF00C853.toInt())
            tabs.forEachIndexed { idx, btn ->
                if (idx == selected) {
                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(colors[idx])
                    btn.setTextColor(0xFF000000.toInt())
                    panels[idx]?.visibility = View.VISIBLE
                } else {
                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
                    btn.setTextColor(0xFFAAAAAA.toInt())
                    panels[idx]?.visibility = View.GONE
                }
            }
        }

        btnTabScreen.setOnClickListener { selectTab(0) }
        btnTabCamera.setOnClickListener { selectTab(1) }
        btnTabMic.setOnClickListener   { selectTab(2) }
    }

    private fun setupControls() {
        val act = activity as? MainActivity ?: return

        // Screen interval controls
        updateIntervalLabel()
        btnScreenFaster?.setOnClickListener {
            if (intervalIdx > 0) { intervalIdx--; updateIntervalLabel() }
        }
        btnScreenSlower?.setOnClickListener {
            if (intervalIdx < intervalOptions.size - 1) { intervalIdx++; updateIntervalLabel() }
        }

        btnToggleScreen?.setOnClickListener {
            if (!screenRunning) {
                screenIntervalMs = intervalOptions[intervalIdx]
                act.wsManager?.sendCommandObj(org.json.JSONObject().apply {
                    put("command", "start_screen_stream")
                    put("interval", screenIntervalMs)
                })
                screenRunning = true
                btnToggleScreen?.text = "■ STOP SCREEN"
                btnToggleScreen?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFFB71C1C.toInt())
                btnToggleScreen?.setTextColor(0xFFFFFFFF.toInt())
                dotScreen?.setBackgroundResource(R.drawable.circle_green)
                pulseView(dotScreen)
                tvScreenInfo?.text = "Receiving..."
            } else {
                act.wsManager?.sendCommand("stop_screen_stream")
                screenRunning = false
                btnToggleScreen?.text = "▶ START SCREEN"
                btnToggleScreen?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt())
                btnToggleScreen?.setTextColor(0xFF000000.toInt())
                dotScreen?.setBackgroundResource(R.drawable.circle_red)
                tvScreenInfo?.text = "Stopped"
            }
        }

        btnToggleCamera?.setOnClickListener {
            if (!cameraRunning) {
                act.wsManager?.sendCommandObj(org.json.JSONObject().apply {
                    put("command", "start_camera_stream")
                    put("interval", 2000L)
                })
                cameraRunning = true
                btnToggleCamera?.text = "■ STOP CAMERA"
                btnToggleCamera?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFFB71C1C.toInt())
                dotCamera?.setBackgroundResource(R.drawable.circle_green)
                pulseView(dotCamera)
                tvCameraInfo?.text = "Receiving..."
            } else {
                act.wsManager?.sendCommand("stop_camera_stream")
                cameraRunning = false
                btnToggleCamera?.text = "▶ START CAMERA"
                btnToggleCamera?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFF6A1B9A.toInt())
                dotCamera?.setBackgroundResource(R.drawable.circle_red)
                tvCameraInfo?.text = "Stopped"
            }
        }

        btnToggleMic?.setOnClickListener {
            if (!micRunning) {
                act.wsManager?.sendCommand("start_mic_stream")
                micRunning = true
                btnToggleMic?.text = "■ STOP LISTENING"
                btnToggleMic?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFFB71C1C.toInt())
                btnToggleMic?.setTextColor(0xFFFFFFFF.toInt())
                dotMic?.setBackgroundResource(R.drawable.circle_green)
                pulseView(dotMic)
                tvMicStatus?.text = "Listening..."
                tvMicStatus?.setTextColor(0xFF00C853.toInt())
            } else {
                act.wsManager?.sendCommand("stop_mic_stream")
                micRunning = false
                btnToggleMic?.text = "▶ START LISTENING"
                btnToggleMic?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
                btnToggleMic?.setTextColor(0xFF000000.toInt())
                dotMic?.setBackgroundResource(R.drawable.circle_red)
                tvMicStatus?.text = "Mic Off"
                tvMicStatus?.setTextColor(0xFF555555.toInt())
            }
        }
    }

    private fun updateIntervalLabel() {
        val ms = intervalOptions[intervalIdx]
        tvScreenInterval?.text = when {
            ms >= 1000 -> "${ms / 1000}s"
            else -> "${ms}ms"
        }
    }

    // ── Frame display — recycle old bitmap to prevent OOM ─────────────
    fun onScreenFrame(b64: String) {
        try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val old = lastScreenBitmap
            lastScreenBitmap = bmp
            imgScreen?.setImageBitmap(bmp)
            old?.recycle()
            screenFrameCount++
            if (!screenRunning) {
                screenRunning = true
                btnToggleScreen?.text = "■ STOP SCREEN"
                btnToggleScreen?.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFFB71C1C.toInt())
                btnToggleScreen?.setTextColor(0xFFFFFFFF.toInt())
                dotScreen?.setBackgroundResource(R.drawable.circle_green)
            }
            tvScreenInfo?.text = "Live"
        } catch (_: Exception) {}
    }

    fun onCameraFrame(b64: String) {
        try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val old = lastCameraBitmap
            lastCameraBitmap = bmp
            imgCamera?.setImageBitmap(bmp)
            old?.recycle()
            cameraFrameCount++
            tvCameraInfo?.text = "Live"
        } catch (_: Exception) {}
    }

    private fun pulseView(v: View?) {
        v ?: return
        ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.4f, 1f).apply {
            duration = 600; repeatCount = ObjectAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.4f, 1f).apply {
            duration = 600; repeatCount = ObjectAnimator.INFINITE; start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fpsHandler.removeCallbacks(fpsRunnable)
        lastScreenBitmap?.recycle(); lastScreenBitmap = null
        lastCameraBitmap?.recycle(); lastCameraBitmap = null
    }
}
