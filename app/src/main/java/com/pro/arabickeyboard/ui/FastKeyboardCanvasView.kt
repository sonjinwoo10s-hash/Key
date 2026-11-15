package com.pro.arabickeyboard.ui

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.pro.arabickeyboard.ai.GeminiAiClient
import kotlin.math.abs

class FastKeyboardCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface KeyboardListener {
        fun onCharacterCommit(text: String)
        fun onDelete(count: Int = 1)
        fun onEnter()
        fun onCursorMove(delta: Int)
        fun onOpenEmojiPanel()
    }

    var listener: KeyboardListener? = null
    private var isSymbolsMode = false

    private val bgPaint = Paint().apply { color = Color.parseColor("#0B0B0E") }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#242429") }
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3A44") }
    private val specialKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#18181D") }
    private val enterKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B5CE6") }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#32323D")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9AA6")
        textAlign = Paint.Align.CENTER
    }

    fun applySavedTheme() {
        val (bg, key, accent) = GeminiAiClient.getActiveTheme(context)
        bgPaint.color = bg
        keyPaint.color = key
        enterKeyPaint.color = accent
        invalidate()
    }

    data class KeyRect(
        val label: String,
        val code: String,
        val rect: RectF = RectF(),
        val weight: Float = 1f,
        val isSpecial: Boolean = false,
        val isEnter: Boolean = false
    )

    private val arabicRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0").map { KeyRect(it, it) },
        listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج").map { KeyRect(it, it) },
        listOf("ش","س","ي","ب","ل","ا","ت","ن","م","ك","ط").map { KeyRect(it, it) },
        listOf(
            KeyRect("ذ", "ذ"), KeyRect("ء", "ء"), KeyRect("ؤ", "ؤ"), KeyRect("ر", "ر"),
            KeyRect("ى", "ى"), KeyRect("ة", "ة"), KeyRect("و", "و"), KeyRect("ز", "ز"),
            KeyRect("ظ", "ظ"), KeyRect("د", "د"), KeyRect("⌫", "DEL", weight = 1.35f, isSpecial = true)
        ),
        listOf(
            KeyRect("?123", "SYM", weight = 1.3f, isSpecial = true),
            KeyRect("،", "،", weight = 0.9f, isSpecial = true),
            KeyRect("😊", "EMOJI", weight = 0.9f, isSpecial = true),
            KeyRect("‹ العربية ›", "SPACE", weight = 4.2f),
            KeyRect(".", ".", weight = 0.9f, isSpecial = true),
            KeyRect("✓", "ENTER", weight = 1.3f, isEnter = true)
        )
    )

    private var activeKey: KeyRect? = null
    private var spaceStartX = 0f
    private var isDraggingSpace = false

    private val deleteHandler = Handler(Looper.getMainLooper())
    private var deleteHoldTicks = 0
    private val repeatDeleteRunnable = object : Runnable {
        override fun run() {
            deleteHoldTicks++
            val step = if (deleteHoldTicks > 8) 3 else 1
            listener?.onDelete(step)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            deleteHandler.postDelayed(this, 42L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applySavedTheme()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyBounds(w.toFloat(), h.toFloat())
        textPaint.textSize = h * 0.082f
        subTextPaint.textSize = h * 0.055f
    }

    private fun calculateKeyBounds(width: Float, height: Float) {
        val rowCount = arabicRows.size
        val verticalPad = 8f
        val horizontalPad = 5f
        val rowHeight = (height - (verticalPad * (rowCount + 1))) / rowCount

        arabicRows.forEachIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            var currentX = horizontalPad
            val top = verticalPad + rowIndex * (rowHeight + verticalPad)
            val bottom = top + rowHeight

            row.forEach { key ->
                val keyWidth = ((width - horizontalPad * (row.size + 1)) * (key.weight / totalWeight))
                key.rect.set(currentX, top, currentX + keyWidth, bottom)
                currentX += keyWidth + horizontalPad
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val cornerRadius = 18f

        arabicRows.forEach { row ->
            row.forEach { key ->
                val paint = when {
                    key === activeKey -> keyPressedPaint
                    key.isEnter -> enterKeyPaint
                    key.isSpecial -> specialKeyPaint
                    else -> keyPaint
                }

                if (key.isEnter) {
                    canvas.drawRoundRect(key.rect, 36f, 36f, paint)
                } else {
                    canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, paint)
                }

                val textY = key.rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                if (key.code == "SPACE") {
                    canvas.drawText(key.label, key.rect.centerX(), textY, subTextPaint)
                } else {
                    canvas.drawText(key.label, key.rect.centerX(), textY, textPaint)
                }
            }
        }

        activeKey?.let { key ->
            if (!key.isSpecial && !key.isEnter && key.code != "SPACE") {
                val popW = key.rect.width() * 1.25f
                val popH = key.rect.height() * 1.35f
                val popLeft = key.rect.centerX() - popW / 2
                val popTop = (key.rect.top - popH - 10f).coerceAtLeast(4f)
                val popRect = RectF(popLeft, popTop, popLeft + popW, popTop + popH)

                canvas.drawRoundRect(popRect, 20f, 20f, popupPaint)
                val popTextY = popRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText(key.label, popRect.centerX(), popTextY, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activeKey?.let { prev ->
                    if (prev.code != "DEL" && prev.code != "SPACE") triggerKeyCommit(prev)
                }

                val touched = findKeyAt(x, y)
                activeKey = touched
                isDraggingSpace = false

                if (touched != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (touched.code == "DEL") {
                        deleteHoldTicks = 0
                        listener?.onDelete(1)
                        deleteHandler.postDelayed(repeatDeleteRunnable, 280L)
                    } else if (touched.code == "SPACE") {
                        spaceStartX = x
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeKey?.code == "SPACE") {
                    val dx = x - spaceStartX
                    if (abs(dx) > 20f) {
                        isDraggingSpace = true
                        val steps = (dx / 20f).toInt()
                        if (steps != 0) {
                            listener?.onCursorMove(steps)
                            spaceStartX = x
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                } else if (activeKey?.code != "DEL") {
                    val current = findKeyAt(x, y)
                    if (current !== activeKey) {
                        activeKey = current
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                deleteHandler.removeCallbacks(repeatDeleteRunnable)
                activeKey?.let { key ->
                    if (key.code == "SPACE") {
                        if (!isDraggingSpace) listener?.onCharacterCommit(" ")
                    } else if (key.code != "DEL") {
                        triggerKeyCommit(key)
                    }
                }
                activeKey = null
                isDraggingSpace = false
                invalidate()
            }
        }
        return true
    }

    private fun triggerKeyCommit(key: KeyRect) {
        when (key.code) {
            "ENTER" -> listener?.onEnter()
            "EMOJI" -> listener?.onOpenEmojiPanel()
            "SYM" -> isSymbolsMode = !isSymbolsMode
            else -> listener?.onCharacterCommit(key.label)
        }
    }

    private fun findKeyAt(x: Float, y: Float): KeyRect? {
        for (row in arabicRows) {
            for (key in row) {
                if (key.rect.contains(x, y)) return key
            }
        }
        return null
    }
}
