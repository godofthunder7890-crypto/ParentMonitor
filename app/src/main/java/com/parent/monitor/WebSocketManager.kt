package com.parent.monitor

import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class WebSocketManager(
    private var serverUrl: String,
    private var pairCode: String,
    val onMessage: (JSONObject) -> Unit,
    val onConnected: () -> Unit,
    val onDisconnected: () -> Unit
) {
    private var client: WebSocketClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldReconnect = true
    var pingMs: Long = 0
    private var pingTime: Long = 0

    fun connect() { shouldReconnect = true; connectInternal() }

    private fun connectInternal() {
        try {
            client?.close()
            client = object : WebSocketClient(URI(serverUrl)) {
                override fun onOpen(h: ServerHandshake?) {
                    // Send register + pair_code on connect
                    send(JSONObject().apply {
                        put("type", "register")
                        put("role", "parent")
                        put("pair_code", pairCode)
                    }.toString())
                    handler.post { onConnected() }
                    startPing()
                }
                override fun onMessage(message: String?) {
                    if (message == null) return
                    try {
                        val data = JSONObject(message)
                        when (data.optString("type")) {
                            "pong"    -> pingMs = System.currentTimeMillis() - pingTime
                            "auth_ok" -> { }
                            "error"   -> {
                                if (data.optString("reason") == "wrong_pair_code") {
                                    shouldReconnect = false
                                    handler.post { onDisconnected() }
                                }
                            }
                            else -> handler.post { onMessage(data) }
                        }
                    } catch (_: Exception) {}
                }
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    handler.post { onDisconnected() }
                    if (shouldReconnect) handler.postDelayed({ connectInternal() }, 3000)
                }
                override fun onError(ex: Exception?) {
                    if (shouldReconnect) handler.postDelayed({ connectInternal() }, 3000)
                }
            }
            client?.connect()
        } catch (_: Exception) {
            if (shouldReconnect) handler.postDelayed({ connectInternal() }, 3000)
        }
    }

    private fun startPing() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (client?.isOpen == true) {
                    pingTime = System.currentTimeMillis()
                    try { client?.send(JSONObject().apply { put("type", "ping") }.toString()) }
                    catch (_: Exception) {}
                    handler.postDelayed(this, 5000)
                }
            }
        }, 5000)
    }

    fun sendCommand(command: String) {
        try {
            client?.send(JSONObject().apply {
                put("type", "command"); put("command", command)
            }.toString())
        } catch (_: Exception) {}
    }

    fun sendCommandObj(data: JSONObject) {
        try {
            data.put("type", "command")
            client?.send(data.toString())
        } catch (_: Exception) {}
    }

    fun updateUrl(newUrl: String, newPairCode: String = pairCode) {
        serverUrl = newUrl
        pairCode = newPairCode
        disconnect()
        connect()
    }

    fun disconnect() { shouldReconnect = false; client?.close() }
    fun isConnected() = client?.isOpen == true
}
