package com.parent.monitor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MessagesFragment : Fragment(R.layout.fragment_messages) {

    companion object {
        private const val RELAY = "https://relay-server-production-bf46.up.railway.app"
        private val QUICK_REPLIES = listOf(
            "OK, be safe! 💙", "Coming to pick you up! 🚗",
            "Call me now 📞", "Don't worry, I'm here ❤️", "Stay where you are!"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val http    = OkHttpClient()
    private val adapter = MessageAdapter()

    private lateinit var recycler:     RecyclerView
    private lateinit var etReply:      EditText
    private lateinit var btnSend:      Button
    private lateinit var quickChips:   LinearLayout
    private lateinit var progressBar:  ProgressBar
    private lateinit var tvEmpty:      TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler   = view.findViewById(R.id.rvMessages)
        etReply    = view.findViewById(R.id.etReplyText)
        btnSend    = view.findViewById(R.id.btnSendReply)
        quickChips = view.findViewById(R.id.quickChipsContainer)
        progressBar= view.findViewById(R.id.msgProgress)
        tvEmpty    = view.findViewById(R.id.tvMessagesEmpty)

        recycler.layoutManager = LinearLayoutManager(context).also { it.stackFromEnd = true }
        recycler.adapter = adapter

        buildQuickChips()
        btnSend.setOnClickListener { sendParentMessage(etReply.text.toString().trim()) }
        loadMessages()
        AnimationUtils.fadeSlideUp(tvEmpty, 0)
    }

    fun onChildMessage(msg: JSONObject) {
        handler.post {
            adapter.add(msg)
            recycler.scrollToPosition(adapter.itemCount - 1)
            tvEmpty.visibility = View.GONE
            if (msg.optBoolean("isDistress", false)) {
                AnimationUtils.shake(recycler)
            }
        }
    }

    private fun pairCode() = (activity as? MainActivity)
        ?.getSharedPreferences("config", 0)?.getString("pair_code","") ?: ""

    private fun loadMessages() {
        val code = pairCode(); if (code.isEmpty()) return
        progressBar.visibility = View.VISIBLE
        val req = Request.Builder().url("$RELAY/api/messages/$code").build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { progressBar.visibility = View.GONE }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                handler.post {
                    progressBar.visibility = View.GONE
                    try {
                        val arr = JSONObject(body).optJSONArray("messages") ?: return@post
                        val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                        if (list.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            tvEmpty.visibility = View.GONE
                            adapter.setAll(list)
                            recycler.scrollToPosition(list.size - 1)
                        }
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun sendParentMessage(text: String) {
        if (text.isEmpty()) return
        val code = pairCode(); if (code.isEmpty()) return
        val body = JSONObject().apply { put("text", text) }.toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$RELAY/api/messages/$code").post(body).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                handler.post {
                    val msg = JSONObject().apply {
                        put("sender","parent"); put("text",text); put("ts",System.currentTimeMillis())
                    }
                    adapter.add(msg)
                    recycler.scrollToPosition(adapter.itemCount - 1)
                    etReply.setText("")
                    tvEmpty.visibility = View.GONE
                }
            }
        })
    }

    private fun buildQuickChips() {
        QUICK_REPLIES.forEach { reply ->
            val chip = TextView(context).apply {
                text = reply; textSize = 12f
                setTextColor(0xFF00E5FF.toInt())
                setBackgroundColor(0xFF0D1033.toInt())
                setPadding(24, 12, 24, 12)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 12, 0)
                layoutParams = lp
                setOnClickListener { sendParentMessage(reply) }
            }
            quickChips.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        http.dispatcher.executorService.shutdown()
    }
}
