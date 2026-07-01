package com.parent.monitor

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class AiInsightsFragment : Fragment(R.layout.fragment_ai_insights) {

    companion object {
        private const val RELAY_DEFAULT = "https://relay-server-production-bf46.up.railway.app"
    }

    private val handler  = Handler(Looper.getMainLooper())
    private val http     = OkHttpClient()

    private fun relayHttp(): String {
        val prefs = (activity as? MainActivity)
            ?.getSharedPreferences(MainActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val ws = prefs?.getString(MainActivity.KEY_SERVER_URL, RELAY_DEFAULT) ?: RELAY_DEFAULT
        return if (ws.startsWith("ws://")) ws.replace("ws://", "http://").removeSuffix("/api/ws")
               else ws.replace("wss://", "https://").removeSuffix("/api/ws")
    }

    private lateinit var scoreCanvas:    ScoreCircleView
    private lateinit var tvGrade:        TextView
    private lateinit var tvMoodEmoji:    TextView
    private lateinit var tvMoodLabel:    TextView
    private lateinit var tvHighlights:   TextView
    private lateinit var tvConcerns:     TextView
    private lateinit var tvSuggestions:  TextView
    private lateinit var tvUpdatedAt:    TextView
    private lateinit var btnAnalyze:     Button
    private lateinit var progressBar:    ProgressBar
    private lateinit var layoutResult:   LinearLayout
    private lateinit var tvEmpty:        TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scoreCanvas   = view.findViewById(R.id.scoreCircle)
        tvGrade       = view.findViewById(R.id.tvGrade)
        tvMoodEmoji   = view.findViewById(R.id.tvMoodEmoji)
        tvMoodLabel   = view.findViewById(R.id.tvMoodLabel)
        tvHighlights  = view.findViewById(R.id.tvHighlights)
        tvConcerns    = view.findViewById(R.id.tvConcerns)
        tvSuggestions = view.findViewById(R.id.tvSuggestions)
        tvUpdatedAt   = view.findViewById(R.id.tvUpdatedAt)
        btnAnalyze    = view.findViewById(R.id.btnAnalyzeNow)
        progressBar   = view.findViewById(R.id.aiProgress)
        layoutResult  = view.findViewById(R.id.layoutResult)
        tvEmpty       = view.findViewById(R.id.tvAiEmpty)

        AnimationUtils.fadeSlideUp(tvEmpty, 0)
        btnAnalyze.setOnClickListener { triggerAnalysis() }
        loadInsights()
    }

    private fun pairCode() = (activity as? MainActivity)
        ?.getSharedPreferences("config", 0)?.getString("pair_code","") ?: ""

    private fun loadInsights() {
        val code = pairCode(); if (code.isEmpty()) return
        progressBar.visibility = View.VISIBLE
        val req = Request.Builder().url("${relayHttp()}/api/ai/insights/$code").build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { progressBar.visibility = View.GONE }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                handler.post {
                    progressBar.visibility = View.GONE
                    try { renderInsights(JSONObject(body)) } catch (_: Exception) {}
                }
            }
        })
    }

    private fun renderInsights(data: JSONObject) {
        val behavior = data.optJSONObject("behavior")
        val mood     = data.optJSONObject("mood")

        if (behavior == null && mood == null) {
            tvEmpty.visibility = View.VISIBLE; layoutResult.visibility = View.GONE; return
        }
        tvEmpty.visibility = View.GONE; layoutResult.visibility = View.VISIBLE

        behavior?.let { b ->
            val score = b.optInt("score", 0)
            val grade = b.optString("grade", "?")
            scoreCanvas.animateTo(score)
            AnimationUtils.popIn(tvGrade, 800)
            tvGrade.text = grade
            tvGrade.setTextColor(ThemeColors.gradeColor(grade))

            val highlights = (0 until (b.optJSONArray("highlights")?.length() ?: 0))
                .map { "✅ " + b.optJSONArray("highlights")!!.optString(it) }.joinToString("\n")
            val concerns = (0 until (b.optJSONArray("concerns")?.length() ?: 0))
                .map { "⚠️ " + b.optJSONArray("concerns")!!.optString(it) }.joinToString("\n")
            val suggestions = (0 until (b.optJSONArray("suggestions")?.length() ?: 0))
                .map { "💡 " + b.optJSONArray("suggestions")!!.optString(it) }.joinToString("\n")

            tvHighlights.text  = highlights.ifEmpty { "No highlights yet" }
            tvConcerns.text    = concerns.ifEmpty    { "No concerns" }
            tvSuggestions.text = suggestions.ifEmpty { "No suggestions" }
        }

        mood?.let { m ->
            val moodStr = m.optString("mood", "normal")
            tvMoodEmoji.text = ThemeColors.moodEmoji(moodStr)
            tvMoodLabel.text = moodStr.replaceFirstChar { it.uppercase() }
            tvMoodLabel.setTextColor(ThemeColors.moodColor(moodStr))
            AnimationUtils.breathe(tvMoodEmoji)
        }

        data.optString("updatedAt").takeIf { it.isNotEmpty() }?.let {
            tvUpdatedAt.text = "Last updated: ${it.take(16).replace('T',' ')}"
        }

        AnimationUtils.staggerCards(tvHighlights, tvConcerns, tvSuggestions)
    }

    private fun triggerAnalysis() {
        val code = pairCode(); if (code.isEmpty()) return
        btnAnalyze.isEnabled = false
        btnAnalyze.text = "Analyzing..."
        progressBar.visibility = View.VISIBLE
        val req = Request.Builder().url("${relayHttp()}/api/ai/analyze/$code")
            .post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { reset() }
            }
            override fun onResponse(call: Call, response: Response) {
                handler.postDelayed({
                    reset()
                    loadInsights()
                }, 8000)
            }
        })
    }

    private fun reset() {
        btnAnalyze.isEnabled = true; btnAnalyze.text = "ANALYZE NOW"
        progressBar.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // FIX: Do not shut down the OkHttpClient dispatcher here — shutting it down
        // on view destruction (e.g. during ViewPager swipe) permanently breaks future
        // requests if the Fragment instance survives view recreation. Cancel in-flight
        // calls by tag instead, and let the client live for the Fragment's lifetime.
        http.dispatcher.cancelAll()
    }
}
