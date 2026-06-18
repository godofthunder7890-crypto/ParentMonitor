package com.parent.monitor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val dashboardFragment   = DashboardFragment()
    val liveFragment        = LiveFragment()
    val dataFragment        = DataFragment()
    val controlFragment     = ControlFragment()
    val notifFragment       = NotificationsFragment()
    val settingsFragment    = SettingsFragment()

    override fun getItemCount() = 6

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> dashboardFragment
        1 -> liveFragment
        2 -> dataFragment
        3 -> controlFragment
        4 -> notifFragment
        else -> settingsFragment
    }
}
