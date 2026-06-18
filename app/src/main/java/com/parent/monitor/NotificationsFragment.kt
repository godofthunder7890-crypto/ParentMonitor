package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotificationsFragment : Fragment() {

    val adapter = NotificationAdapter()
    private var tvCount: TextView? = null
    private var tvEmpty: TextView? = null
    private var rv: RecyclerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)
        tvCount = view.findViewById(R.id.tvNotifCount)
        tvEmpty = view.findViewById(R.id.tvNotifEmpty)
        rv      = view.findViewById(R.id.rvNotifications)

        rv?.layoutManager = LinearLayoutManager(context)
        rv?.adapter = adapter

        // Register a data observer to update count + empty state
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(pos: Int, count: Int) { updateUi(); scrollTop() }
            override fun onChanged() { updateUi() }
        })

        view.findViewById<Button>(R.id.btnClear).setOnClickListener {
            adapter.clear(); updateUi()
        }
        (activity as? MainActivity)?.notificationsFragment = this
        return view
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.notificationsFragment = null
        super.onDestroyView()
    }

    fun addNotification(item: NotificationItem) {
        adapter.addNotification(item)
        // updateUi() called via observer
    }

    private fun updateUi() {
        val count = adapter.itemCount
        tvCount?.text = count.toString()
        tvEmpty?.visibility = if (count == 0) View.VISIBLE else View.GONE
        rv?.visibility = if (count > 0) View.VISIBLE else View.INVISIBLE
    }

    private fun scrollTop() {
        rv?.layoutManager?.let {
            (it as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        }
    }
}
