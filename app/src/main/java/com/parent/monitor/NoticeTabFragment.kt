package com.parent.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class NoticeTabFragment : Fragment() {

    private lateinit var container: LinearLayout

    data class NoticeItem(
        val iconRes: Int,
        val iconTint: Int,
        val title: String,
        var preview: String = "No data available",
        var hasUnread: Boolean = false,
        val tabIndex: Int = -1
    )

    private val items = listOf(
        NoticeItem(R.drawable.ic_notice_app_notifications, 0xFF1976D2.toInt(), "App Notifications",
            tabIndex = 6),
        NoticeItem(R.drawable.ic_notice_alerts, 0xFFF57C00.toInt(), "Alerts & Request",
            tabIndex = 10),
        NoticeItem(R.drawable.ic_notice_browser, 0xFF0288D1.toInt(), "Browser Safety",
            tabIndex = 14),
        NoticeItem(R.drawable.ic_notice_video, 0xFF212121.toInt(), "TikTok & YouTube History",
            tabIndex = 15),
        NoticeItem(R.drawable.ic_notice_snapshot, 0xFF7C4DFF.toInt(), "Snapshot & Recording",
            tabIndex = 24),
        NoticeItem(R.drawable.ic_notice_usage, 0xFFE91E63.toInt(), "Usage Logs",
            tabIndex = 11),
        NoticeItem(R.drawable.ic_notice_social, 0xFF673AB7.toInt(), "Social App Keyword Detection",
            tabIndex = 25),
        NoticeItem(R.drawable.ic_notice_call, 0xFF388E3C.toInt(), "Call & SMS Safety",
            tabIndex = 21),
        NoticeItem(R.drawable.ic_notice_albums, 0xFFF57C00.toInt(), "Albums Safety",
            tabIndex = 17)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_notice_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.noticeContainer)
        populateCards()
    }

    private fun populateCards() {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (item in items) {
            val card = inflater.inflate(R.layout.item_notice_card, container, false)

            val ivIcon = card.findViewById<ImageView>(R.id.ivNoticeIcon)
            val tvTitle = card.findViewById<TextView>(R.id.tvNoticeTitle)
            val tvPreview = card.findViewById<TextView>(R.id.tvNoticePreview)
            val badge = card.findViewById<View>(R.id.viewBadge)

            ivIcon.setImageResource(item.iconRes)
            ivIcon.setBackgroundResource(0)
            ivIcon.setPadding(0, 0, 0, 0)
            ivIcon.clearColorFilter()

            tvTitle.text = item.title
            tvPreview.text = item.preview
            badge.visibility = if (item.hasUnread) View.VISIBLE else View.GONE

            if (item.tabIndex >= 0) {
                card.setOnClickListener {
                    (activity as? MainActivity)?.openFragment(item.tabIndex)
                }
            }

            container.addView(card)
        }
    }

    fun updatePreview(tabIndex: Int, preview: String, hasUnread: Boolean = false) {
        val item = items.find { it.tabIndex == tabIndex } ?: return
        item.preview = preview
        item.hasUnread = hasUnread
        if (isAdded) populateCards()
    }

    fun setAlertBadge(tabIndex: Int, preview: String) {
        updatePreview(tabIndex, preview, hasUnread = true)
        (activity as? MainActivity)?.showNoticeBadge(true)
    }
}
