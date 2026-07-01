package com.parent.monitor

import android.app.Application

class ParentMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
