package com.parent.monitor

data class NotificationItem(
    val app: String,
    val title: String,
    val text: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis()
)
