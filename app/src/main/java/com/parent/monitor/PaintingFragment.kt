package com.parent.monitor

import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject

/**
 * PaintingFragment — SafeWatch Blueprint: "Live Painting"
 * Parent draws on this canvas → strokes are relayed to child's screen overlay.
 */
class PaintingFragment : Fragment() {

    private lateinit var paintCanvas:  PaintCanvasView
    private lateinit var btnClear:     Button
    private lateinit var btnUndo:      Button
    private lateinit var tvStatus:     TextView
    private lateinit var seekSize:     SeekBar
    private lateinit var tvSizeLabel:  TextView

    // Colour swatches
    private var currentColor = "#FF4444"
    private val colors = listOf(
        "#FF4444" to "🔴",
        "#FF9800" to "🟠",
        "#FFEB3B" to "🟡",
        "#00C853" to "🟢",
        "#00E5FF" to "🔵",
        "#AA00FF" to "🟣",
        "#FFFFFF" to "⬜",
        "#000000" to "⬛"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF060612.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── Top toolbar ─────────────────────────────────────────────────────
        val toolbar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp, 8.dp, 8.dp, 4.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvStatus = TextView(requireContext()).apply {
            text = "🖌  Draw on child's screen"; textSize = 12f; setTextColor(0xFF00E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnUndo = Button(requireContext()).apply {
            text = "↩"; textSize = 14f; setPadding(16.dp, 4.dp, 16.dp, 4.dp)
            setBackgroundColor(0xFF1A1A2E.toInt()); setTextColor(0xFFFFFFFF.toInt())
        }
        btnClear = Button(requireContext()).apply {
            text = "CLEAR"; textSize = 12f; setPadding(16.dp, 4.dp, 16.dp, 4.dp)
            setBackgroundColor(0xFFFF1744.toInt()); setTextColor(0xFFFFFFFF.toInt())
        }
        toolbar.addView(tvStatus); toolbar.addView(btnUndo); toolbar.addView(btnClear)
        root.addView(toolbar)

        // ── Brush size ──────────────────────────────────────────────────────
        val sizeRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp, 0, 8.dp, 4.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tvSizeLabel = TextView(requireContext()).apply {
            text = "Size: 12"; textSize = 12f; setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 8.dp, 0)
        }
        seekSize = SeekBar(requireContext()).apply {
            max = 56; progress = 8   // 4..60 → progress+4
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sizeRow.addView(tvSizeLabel); sizeRow.addView(seekSize)
        root.addView(sizeRow)

        // ── Colour picker row ───────────────────────────────────────────────
        val colorRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp, 0, 8.dp, 8.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        colors.forEach { (hex, emoji) ->
            val btn = Button(requireContext()).apply {
                text = emoji; textSize = 16f; setPadding(4.dp, 2.dp, 4.dp, 2.dp)
                val lp = LinearLayout.LayoutParams(0, 48.dp, 1f)
                layoutParams = lp
                try { setBackgroundColor(Color.parseColor(hex)) } catch (_: Exception) { setBackgroundColor(0xFF222233.toInt()) }
                setTextColor(if (hex == "#000000") Color.WHITE else Color.BLACK)
                setOnClickListener {
                    currentColor = hex
                    tvStatus.text = "🖌  Color: $emoji  |  Draw on child's screen"
                    paintCanvas.setColor(hex)
                }
            }
            colorRow.addView(btn)
        }
        root.addView(colorRow)

        // ── Paint canvas ────────────────────────────────────────────────────
        paintCanvas = PaintCanvasView(requireContext()) { x, y, action ->
            sendStroke(x, y, action)
        }
        paintCanvas.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        root.addView(paintCanvas)

        // ── Hint label ──────────────────────────────────────────────────────
        val hint = TextView(requireContext()).apply {
            text = "Touch here → appears on child's screen in real time"
            textSize = 11f; setTextColor(0xFF555566.toInt()); gravity = Gravity.CENTER
            setPadding(0, 4.dp, 0, 8.dp)
        }
        root.addView(hint)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnClear.setOnClickListener {
            paintCanvas.clear()
            (activity as? MainActivity)?.sendToChild(
                JSONObject().apply { put("command", "clear_painting") }
            )
        }

        btnUndo.setOnClickListener {
            paintCanvas.undo()
        }

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                val size = p + 4
                tvSizeLabel.text = "Size: $size"
                paintCanvas.setSize(size.toFloat())
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar)  {}
        })
    }

    private fun sendStroke(xRatio: Float, yRatio: Float, action: String) {
        val size = (seekSize.progress + 4).toFloat()
        (activity as? MainActivity)?.sendToChild(JSONObject().apply {
            put("command", "paint_stroke")
            put("x",      xRatio.toDouble())
            put("y",      yRatio.toDouble())
            put("color",  currentColor)
            put("size",   size.toDouble())
            put("action", action)
        })
    }

    // ── Touch-capture canvas ────────────────────────────────────────────────

    class PaintCanvasView(
        context: android.content.Context,
        private val onStroke: (Float, Float, String) -> Unit
    ) : View(context) {

        private val paths    = mutableListOf<Triple<Path, Paint, String>>()
        private var curPath: Path?  = null
        private var curPaint: Paint? = null
        private var curColor = "#FF4444"
        private var curSize  = 12f

        fun setColor(hex: String) { curColor = hex }
        fun setSize(s: Float)     { curSize = s }

        fun clear()  { paths.clear(); curPath = null; curPaint = null; invalidate() }
        fun undo()   { if (paths.isNotEmpty()) { paths.removeLastOrNull(); invalidate() } }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Dark drawing area
            canvas.drawColor(0xFF0D0D22.toInt())
            paths.forEach { (path, paint, _) -> canvas.drawPath(path, paint) }
            curPath?.let { canvas.drawPath(it, curPaint!!) }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val xRatio = (event.x / width.toFloat()).coerceIn(0f, 1f)
            val yRatio = (event.y / height.toFloat()).coerceIn(0f, 1f)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    curPath  = Path().apply { moveTo(event.x, event.y) }
                    curPaint = makePaint(curColor, curSize)
                    onStroke(xRatio, yRatio, "down")
                }
                MotionEvent.ACTION_MOVE -> {
                    curPath?.lineTo(event.x, event.y)
                    onStroke(xRatio, yRatio, "move")
                }
                MotionEvent.ACTION_UP   -> {
                    curPath?.lineTo(event.x, event.y)
                    val p = curPath; val pt = curPaint
                    if (p != null && pt != null) paths.add(Triple(p, pt, curColor))
                    curPath = null; curPaint = null
                    onStroke(xRatio, yRatio, "up")
                }
            }
            invalidate()
            return true
        }

        private fun makePaint(hex: String, size: Float) = Paint().apply {
            try { color = Color.parseColor(hex) } catch (_: Exception) { color = Color.RED }
            strokeWidth = size; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
