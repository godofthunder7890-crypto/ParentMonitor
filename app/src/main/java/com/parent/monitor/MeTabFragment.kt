package com.parent.monitor

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth

class MeTabFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_me_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserInfo(view)
        loadAppVersion(view)
        setupClickListeners(view)
    }

    private fun loadUserInfo(v: View) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            v.findViewById<TextView>(R.id.tvUserName)?.text =
                user?.displayName?.takeIf { it.isNotEmpty() } ?: "GuardianEye User"
            v.findViewById<TextView>(R.id.tvUserEmail)?.text =
                user?.email ?: ""
        } catch (_: Exception) {}
    }

    private fun loadAppVersion(v: View) {
        try {
            val versionName = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
            v.findViewById<TextView>(R.id.tvAppVersion)?.text = "V$versionName"
        } catch (_: Exception) {}
    }

    private fun setupClickListeners(v: View) {
        v.findViewById<View>(R.id.cardMyDevices)?.setOnClickListener {
            showSimpleDialog("My Devices", "Paired device:\n• Child Monitor (connected)")
        }

        v.findViewById<View>(R.id.rowHelp)?.setOnClickListener {
            showSimpleDialog("Help",
                "Q: How do I pair with a child device?\nA: Install ChildMonitor on the child's device, then enter the pair code shown in Settings.\n\n" +
                "Q: Why is the child shown as offline?\nA: Both devices must have internet access and the app must be running in the background."
            )
        }

        v.findViewById<View>(R.id.rowFeedback)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("support@guardianeye.app"))
                    putExtra(Intent.EXTRA_SUBJECT, "GuardianEye Feedback")
                }
                startActivity(intent)
            } catch (_: Exception) {
                showSimpleDialog("Feedback", "Please email us at: support@guardianeye.app")
            }
        }

        v.findViewById<View>(R.id.rowRate)?.setOnClickListener {
            showSimpleDialog("Rate", "Thank you for using GuardianEye!\nPlease leave a rating on the Play Store.")
        }

        v.findViewById<View>(R.id.rowLanguage)?.setOnClickListener {
            showSimpleDialog("Language", "Currently available:\n• English")
        }

        v.findViewById<View>(R.id.rowAbout)?.setOnClickListener {
            try {
                val versionName = requireContext().packageManager
                    .getPackageInfo(requireContext().packageName, 0).versionName
                showSimpleDialog("About GuardianEye",
                    "Version: $versionName\n\nGuardianEye is a parental monitoring solution that helps you keep your child safe online.\n\n© 2024 GuardianEye")
            } catch (_: Exception) {
                showSimpleDialog("About GuardianEye", "Parental monitoring app\n© 2024 GuardianEye")
            }
        }

        v.findViewById<View>(R.id.cardRedeem)?.setOnClickListener {
            showSimpleDialog("Redeem", "Redeem feature coming soon!\nStay tuned for updates.")
        }

        v.findViewById<View>(R.id.btnSubscribe)?.setOnClickListener {
            showSimpleDialog("Subscription", "PRO features include:\n• Remote Camera\n• Screen Mirroring\n• One-Way Audio\n• Social App Detection\n• Browser Safety\n\nContact us to unlock PRO features.")
        }
    }

    private fun showSimpleDialog(title: String, message: String) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
