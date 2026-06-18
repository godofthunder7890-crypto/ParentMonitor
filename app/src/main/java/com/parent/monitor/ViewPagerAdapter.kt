package com.parent.monitor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val dashboardFragment   = DashboardFragment()
    val liveFragment        = LiveFragment()
    val protectFragment     = ProtectFragment()
    val limitsFragment      = LimitsFragment()
    val trackFragment       = TrackFragment()
    val dataFragment        = DataFragment()
    val reportsFragment     = ReportsFragment()
    val notifFragment       = NotificationsFragment()
    val settingsFragment    = SettingsFragment()

    override fun getItemCount() = 9

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> dashboardFragment
        1 -> liveFragment
        2 -> protectFragment
        3 -> limitsFragment
        4 -> trackFragment
        5 -> dataFragment
        6 -> reportsFragment
        7 -> notifFragment
        else -> settingsFragment
    }
}
