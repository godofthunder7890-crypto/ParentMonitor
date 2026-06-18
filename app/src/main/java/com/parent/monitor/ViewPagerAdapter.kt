package com.parent.monitor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) :
    FragmentStateAdapter(activity) {

    val notifFragment = NotificationsFragment()
    val controlFragment = ControlFragment()
    val settingsFragment = SettingsFragment()

    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> notifFragment
            1 -> controlFragment
            2 -> settingsFragment
            else -> notifFragment
        }
    }
    }
