package com.parent.monitor

import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class WebSocketManager(
    private var serverUrl: String,
    val onMessage: (JSONObject) -> Unit,
    val onConnected: () -> Unit,
    val onDisconnected: () -> Unit
) {
    private var client: WebSocketClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldReconnect = true
    var pingMs: Long = 0
    private var pingTime: Long = 0

    fun connect() {
        shouldReconnect = true
        connectInternal()
    }

    private fun connectInternal() {
        try {
            client?.close()
            client = object : WebSocketClient(URI(serverUrl)) {
                override fun onOpen(h: ServerHandshake?) {
                    send(JSONObject().apply {
                        put("type", "register")
                        put("role", "parent")
                    }.toString())
                    handler.post { onConnected() }
                    startPing()
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        try {
                            val data = JSONObject(it)
                            if (data.optString("type") == "pong") {
                                pingMs = System.currentTimeMillis() - pingTime
                            } else {
                                handler.post { onMessage(data) }
                            }
                        } catch (e: Exception) { }
                    }
                }

                override fun onClose(
                    code: Int, reason: String?, remote: Boolean) {
                    handler.post { onDisconnected() }
                    if (shouldReconnect) {
                        handler.postDelayed({ connectInternal() }, 3000)
                    }
                }

                override fun onError(ex: Exception?) {
                    if (shouldReconnect) {
                        handler.postDelayed({ connectInternal() }, 3000)
                    }
                }
            }
            client?.connect()
        } catch (e: Exception) {
            if (shouldReconnect) {
                handler.postDelayed({ connectInternal() }, 3000)
            }
        }
    }

    private fun startPing() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (client?.isOpen == true) {
                    pingTime = System.currentTimeMillis()
                    try {
                        client?.send(JSONObject().apply {
                            put("type", "ping")
                        }.toString())
                    } catch (e: Exception) { }
                    handler.postDelayed(this, 5000)
                }
            }
        }, 5000)
    }

    fun sendCommand(command: String) {
        try {
            client?.send(JSONObject().apply {
                put("type", "command")
                put("command", command)
            }.toString())
        } catch (e: Exception) { }
    }

    fun updateUrl(newUrl: String) {
        serverUrl = newUrl
        disconnect()
        connect()
    }

    fun disconnect() {
        shouldReconnect = false
        client?.close()
    }

    fun isConnected() = client?.isOpen == true
}
