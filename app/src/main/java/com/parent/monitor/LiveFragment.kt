package com.parent.monitor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment

class LiveFragment : Fragment() {

    private var imgScreen: ImageView? = null
    private var imgCamera: ImageView? = null
    private var tvScreenInfo: TextView? = null
    private var tvCameraInfo: TextView? = null
    private var btnToggleScreen: Button? = null
    private var btnToggleCamera: Button? = null
    private var btnToggleMic: Button? = null
    private var dotScreen: View? = null
    private var dotCamera: View? = null
    private var dotMic: View? = null

    private var screenRunning = false
    private var cameraRunning = false
    private var micRunning = false
    private var screenFrames = 0
    private var cameraFrames = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? {
        val v = i.inflate(R.layout.fragment_live, c, false)

        imgScreen      = v.findViewById(R.id.imgScreen)
        imgCamera      = v.findViewById(R.id.imgCamera)
        tvScreenInfo   = v.findViewById(R.id.tvScreenInfo)
        tvCameraInfo   = v.findViewById(R.id.tvCameraInfo)
        btnToggleScreen= v.findViewById(R.id.btnToggleScreen)
        btnToggleCamera= v.findViewById(R.id.btnToggleCamera)
        btnToggleMic   = v.findViewById(R.id.btnToggleMic)
        dotScreen      = v.findViewById(R.id.dotScreen)
        dotCamera      = v.findViewById(R.id.dotCamera)
        dotMic         = v.findViewById(R.id.dotMic)

        val act = requireActivity() as MainActivity

        btnToggleScreen?.setOnClickListener {
            if (!screenRunning) {
                act.wsManager?.sendCommand("start_screen_stream")
                screenRunning = true
                btnToggleScreen?.text = "STOP"
                btnToggleScreen?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4444.toInt())
                dotScreen?.setBackgroundResource(R.drawable.circle_green)
                tvScreenInfo?.text = "Receiving screen... (frames: 0)"
            } else {
                act.wsManager?.sendCommand("stop_screen_stream")
                screenRunning = false
                btnToggleScreen?.text = "START"
                btnToggleScreen?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00D4FF.toInt())
                dotScreen?.setBackgroundResource(R.drawable.circle_red)
                tvScreenInfo?.text = "Stopped"
            }
        }

        btnToggleCamera?.setOnClickListener {
            if (!cameraRunning) {
                act.wsManager?.sendCommand("start_camera_stream")
                cameraRunning = true
                btnToggleCamera?.text = "STOP"
                btnToggleCamera?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4444.toInt())
                dotCamera?.setBackgroundResource(R.drawable.circle_green)
                tvCameraInfo?.text = "Receiving camera... (frames: 0)"
            } else {
                act.wsManager?.sendCommand("stop_camera_stream")
                cameraRunning = false
                btnToggleCamera?.text = "START"
                btnToggleCamera?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF9C27B0.toInt())
                dotCamera?.setBackgroundResource(R.drawable.circle_red)
                tvCameraInfo?.text = "Stopped"
            }
        }

        btnToggleMic?.setOnClickListener {
            if (!micRunning) {
                act.wsManager?.sendCommand("start_mic_stream")
                micRunning = true
                btnToggleMic?.text = "STOP"
                btnToggleMic?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4444.toInt())
                dotMic?.setBackgroundResource(R.drawable.circle_green)
            } else {
                act.wsManager?.sendCommand("stop_mic_stream")
                micRunning = false
                btnToggleMic?.text = "START"
                btnToggleMic?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00FF88.toInt())
                dotMic?.setBackgroundResource(R.drawable.circle_red)
            }
        }

        return v
    }

    fun onScreenFrame(b64: String) {
        try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            screenFrames++
            imgScreen?.setImageBitmap(bmp)
            tvScreenInfo?.text = "Live  •  frames: $screenFrames"
        } catch (_: Exception) {}
    }

    fun onCameraFrame(b64: String) {
        try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            cameraFrames++
            imgCamera?.setImageBitmap(bmp)
            tvCameraInfo?.text = "Live  •  frames: $cameraFrames"
        } catch (_: Exception) {}
    }
}
