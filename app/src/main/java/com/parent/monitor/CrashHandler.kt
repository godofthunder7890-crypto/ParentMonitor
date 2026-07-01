package com.parent.monitor

import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * Global uncaught exception handler.
 * Shows CrashActivity instead of "App has stopped" system dialog.
 */
class CrashHandler private constructor(
    private val ctx: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val intent = Intent(ctx, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(CrashActivity.EXTRA_ERROR,  throwable.message ?: "Unknown error")
                putExtra(CrashActivity.EXTRA_STACK,  throwable.stackTraceToString().take(3000))
            }
            ctx.startActivity(intent)
            Thread.sleep(300)
        } catch (_: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
                ?: Process.killProcess(Process.myPid())
            return
        }
        defaultHandler?.uncaughtException(thread, throwable)
            ?: Process.killProcess(Process.myPid())
    }

    companion object {
        fun install(context: Context) {
            val app = context.applicationContext
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(app, Thread.getDefaultUncaughtExceptionHandler())
            )
        }
    }
}
