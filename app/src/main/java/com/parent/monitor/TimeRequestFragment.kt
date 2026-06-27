package com.parent.monitor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class TimeRequest(
    val requestId: String,
    val minutes: Int,
    val reason: String,
    val timestamp: Long,
    var status: String = "pending"  // pending / approved / denied
)

class TimeRequestFragment : Fragment() {

    private var listContainer: LinearLayout? = null
    private var tvEmpty: TextView? = null
    private val requests = mutableListOf<TimeRequest>()
    private val sdf = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        scroll.addView(root)

        // Header
        root.addView(TextView(ctx).apply {
            text = "⏱  Screen Time Requests"
            setTextColor(0xFF00C853.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        })
        root.addView(TextView(ctx).apply {
            text = "Your child can request extra screen time. Approve or deny below."
            setTextColor(0xFF64748B.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 16)
        })

        // Quick grant buttons
        root.addView(TextView(ctx).apply {
            text = "QUICK GRANT"; setTextColor(0xFF888888.toInt()); textSize = 11f
            setPadding(0, 8, 0, 4)
        })
        val quickRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(15, 30, 60).forEach { mins ->
            val btn = Button(ctx).apply {
                text = "${mins}m"
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1E3A5F.toInt())
                setTextColor(0xFF00C853.toInt())
                textSize = 12f
                setPadding(8, 4, 8, 4)
            }
            btn.setOnClickListener {
                val act = activity as? MainActivity ?: return@setOnClickListener
                act.sendCommandObj(JSONObject().apply {
                    put("command", "time_approved")
                    put("minutes", mins)
                    put("request_id", "quick_${System.currentTimeMillis()}")
                })
                Toast.makeText(ctx, "✅ Granted $mins minutes!", Toast.LENGTH_SHORT).show()
                act.hideBannerIfNoPending()
            }
            quickRow.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            })
        }
        root.addView(quickRow)

        // Divider
        root.addView(View(ctx).apply {
            setBackgroundColor(0xFF1F2937.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, 16, 0, 16)
            }
        })

        // Request list header
        root.addView(TextView(ctx).apply {
            text = "PENDING & RECENT REQUESTS"; setTextColor(0xFF888888.toInt()); textSize = 11f
            setPadding(0, 0, 0, 8)
        })

        tvEmpty = TextView(ctx).apply {
            text = "No requests yet. Your child hasn't sent any time requests."
            setTextColor(0xFF64748B.toInt())
            textSize = 13f
            setPadding(0, 16, 0, 16)
        }
        root.addView(tvEmpty)

        listContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(listContainer)

        (activity as? MainActivity)?.timeRequestFragment = this
        return scroll
    }

    fun onTimeRequest(req: TimeRequest) {
        // Avoid duplicates
        if (requests.any { it.requestId == req.requestId }) return
        requests.add(0, req)
        refreshList()
    }

    private fun refreshList() {
        val container = listContainer ?: return
        container.removeAllViews()
        tvEmpty?.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE

        requests.forEach { req ->
            val ctx = requireContext()
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(when(req.status) {
                    "approved" -> 0xFF0D2B0D.toInt()
                    "denied"   -> 0xFF2B0D0D.toInt()
                    else       -> 0xFF0D1A2B.toInt()
                })
                setPadding(16, 16, 16, 16)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            card.layoutParams = lp

            card.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL

                addView(TextView(ctx).apply {
                    text = "⏱ ${req.minutes} min requested"
                    setTextColor(0xFFF1F5F9.toInt())
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })

                val statusColor = when(req.status) {
                    "approved" -> 0xFF00C853.toInt()
                    "denied"   -> 0xFFFF1744.toInt()
                    else       -> 0xFFFFB300.toInt()
                }
                val statusText = when(req.status) {
                    "approved" -> "✅ Approved"
                    "denied"   -> "❌ Denied"
                    else       -> "⏳ Pending"
                }
                addView(TextView(ctx).apply {
                    text = statusText
                    setTextColor(statusColor)
                    textSize = 12f
                })
            })

            if (req.reason.isNotBlank()) {
                card.addView(TextView(ctx).apply {
                    text = "Reason: ${req.reason}"
                    setTextColor(0xFF94A3B8.toInt())
                    textSize = 12f
                    setPadding(0, 4, 0, 4)
                })
            }

            card.addView(TextView(ctx).apply {
                text = sdf.format(Date(req.timestamp))
                setTextColor(0xFF475569.toInt())
                textSize = 11f
            })

            if (req.status == "pending") {
                val btnRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 10, 0, 0)
                    }
                }
                val btnApprove = Button(ctx).apply {
                    text = "✅ APPROVE"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00C853.toInt())
                    setTextColor(0xFF000000.toInt())
                    textSize = 12f
                }
                val btnDeny = Button(ctx).apply {
                    text = "❌ DENY"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 12f
                }
                btnApprove.setOnClickListener {
                    req.status = "approved"
                    (activity as? MainActivity)?.let { act ->
                        act.sendCommandObj(JSONObject().apply {
                            put("command", "time_approved")
                            put("minutes", req.minutes)
                            put("request_id", req.requestId)
                        })
                        act.hideBannerIfNoPending()
                    }
                    refreshList()
                    Toast.makeText(ctx, "✅ Approved ${req.minutes} minutes!", Toast.LENGTH_SHORT).show()
                }
                btnDeny.setOnClickListener {
                    req.status = "denied"
                    (activity as? MainActivity)?.let { act ->
                        act.sendCommandObj(JSONObject().apply {
                            put("command", "time_denied")
                            put("request_id", req.requestId)
                        })
                        act.hideBannerIfNoPending()
                    }
                    refreshList()
                    Toast.makeText(ctx, "❌ Denied", Toast.LENGTH_SHORT).show()
                }
                btnRow.addView(btnApprove, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,8,0) })
                btnRow.addView(btnDeny, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                card.addView(btnRow)
            }

            container.addView(card)
        }
    }

    fun hasPendingRequests() = requests.any { it.status == "pending" }

    override fun onDestroyView() {
        (activity as? MainActivity)?.timeRequestFragment = null
        super.onDestroyView()
    }
}
