package com.parent.monitor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 20

    override fun createFragment(position: Int): Fragment = when (position) {
        0  -> DashboardFragment()
        1  -> LiveFragment()
        2  -> ControlFragment()
        3  -> ProtectFragment()
        4  -> LimitsFragment()
        5  -> TrackFragment()
        6  -> ReportsFragment()
        7  -> NotificationsFragment()
        8  -> DataFragment()
        9  -> AppsFragment()
        10 -> CallSmsFragment()
        11 -> LocationFragment()
        12 -> FilesFragment()
        13 -> ShizukuFragment()
        14 -> BrowserSafetyFragment()
        15 -> VideoHistoryFragment()
        16 -> RecordingsFragment()
        17 -> AlbumsSafetyFragment()
        18 -> PaintingFragment()
        19 -> SettingsFragment()
        else -> DashboardFragment()
    }

    fun getPageTitle(position: Int): String = when (position) {
        0  -> "Dashboard"
        1  -> "Live"
        2  -> "Control"
        3  -> "Protect"
        4  -> "Limits"
        5  -> "Track"
        6  -> "Reports"
        7  -> "Alerts"
        8  -> "Data"
        9  -> "Apps"
        10 -> "Calls/SMS"
        11 -> "Location"
        12 -> "Files"
        13 -> "Shizuku"
        14 -> "Browser"
        15 -> "Video"
        16 -> "Audio"
        17 -> "Albums"
        18 -> "Painting"
        19 -> "Settings"
        else -> ""
    }
}
