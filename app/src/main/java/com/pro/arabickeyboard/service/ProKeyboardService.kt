package com.pro.arabickeyboard.service

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.ExtractedTextRequest
import android.widget.*
import com.pro.arabickeyboard.ai.GeminiAiClient
import com.pro.arabickeyboard.engine.ZakhrafaEngine
import com.pro.arabickeyboard.ui.FastKeyboardCanvasView
import kotlinx.coroutines.*

class ProKeyboardService : InputMethodService(), FastKeyboardCanvasView.KeyboardListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var mainWrapper: LinearLayout
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var emojiMultiplierStrip: LinearLayout
    private lateinit var dynamicPanelContainer: FrameLayout
    private lateinit var keyboardCanvas: FastKeyboardCanvasView

    private var lastSelectedEmoji = "😂"
    private val pinnedClips = mutableListOf(
        "https://github.com/Jakeschincariol/replica-skill
install skill"
    )
    private val recentClips = mutableListOf(
        "https://play.google.com/store/apps/details?id=com.ayman.keyboard",
        "github.com/Jakeschincariol/replica-skill",
        "السلام عليكم ورحمة الله وبركاته"
    )

    override fun onCreateInputView(): View {
        mainWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        setupTopSuggestionAndToolbar()

        dynamicPanelContainer = FrameLayout(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(250)
            )
        }
        mainWrapper.addView(dynamicPanelContainer)

        keyboardCanvas = FastKeyboardCanvasView(this).apply {
            listener = this@ProKeyboardService
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(250)
            )
        }
        mainWrapper.addView(keyboardCanvas)

        setupClipboardListener()
        return mainWrapper
    }

    private fun setupTopSuggestionAndToolbar() {
        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38))
        }
        updateSuggestions(listOf("السلام عليكم ورحمة الله وبركاته", "في", "من", "على", "إلى"))
        mainWrapper.addView(suggestionStrip)

        emojiMultiplierStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        }
        refreshEmojiMultiplierBar()
        mainWrapper.addView(emojiMultiplierStrip)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#121217"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }

        val tools = listOf(
            "⊞" to { showAllToolsPanel() },
            "✨ AI" to { showAiAssistantBar() },
            "😊" to { showEmojiPanel() },
            "📋" to { showClipboardPanel() },
            "🖱️" to { showTrackpadPanel() },
            "Aあ" to { showTranslatePanel() },
            "ضـ" to { toggleZakhrafaMode() }
        )

        tools.forEach { (icon, action) ->
            val btn = TextView(this).apply {
                text = icon
                setTextColor(if (icon.contains("AI")) Color.parseColor("#4C8DFF") else Color.parseColor("#B8B8C2"))
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { action() }
            }
            toolbar.addView(btn)
        }
        mainWrapper.addView(toolbar)
    }

    // ─── شريط المساعد الذكي المتصل بـ Gemini API مباشرة ───
    private fun showAiAssistantBar() {
        closePanels()
        suggestionStrip.removeAllViews()

        val aiActions = listOf(
            "✨ تصحيح إملائي" to GeminiAiClient.TaskType.PROOFREAD,
            "🔄 إعادة صياغة" to GeminiAiClient.TaskType.REPHRASE,
            "🌐 ترجمة EN" to GeminiAiClient.TaskType.TRANSLATE_EN,
            "💬 رد ذكي" to GeminiAiClient.TaskType.SMART_REPLY
        )

        aiActions.forEach { (label, task) ->
            val chip = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                background = roundedRect("#242432", 14f)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    runGeminiTaskOnCurrentField(task)
                }
            }
            suggestionStrip.addView(chip)
        }
    }

    private fun runGeminiTaskOnCurrentField(task: GeminiAiClient.TaskType) {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
            ?: ic.getTextBeforeCursor(500, 0)?.toString()
            ?: ""

        if (extracted.isBlank()) {
            Toast.makeText(this, "اكتب نصاً أولاً ليعالجه المساعد الذكي ✨", Toast.LENGTH_SHORT).show()
            return
        }

        suggestionStrip.removeAllViews()
        val loadingTv = TextView(this).apply {
            text = "⏳ جاري المعالجة بواسطة Gemini AI..."
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        suggestionStrip.addView(loadingTv)

        serviceScope.launch {
            val result = GeminiAiClient.processText(this@ProKeyboardService, extracted, task)
            result.onSuccess { newText ->
                ic.performContextMenuAction(android.R.id.selectAll)
                ic.commitText(newText, 1)
                updateSuggestions(listOf("تم التحديث بواسطة Gemini ✨", "تراجع"))
            }.onFailure { err ->
                Toast.makeText(this@ProKeyboardService, err.message ?: "حدث خطأ", Toast.LENGTH_LONG).show()
                showAiAssistantBar()
            }
        }
    }

    private fun updateSuggestions(words: List<String>) {
        suggestionStrip.removeAllViews()
        words.forEachIndexed { index, word ->
            val chip = TextView(this).apply {
                text = word
                setTextColor(if (index == 0) Color.parseColor("#4C8DFF") else Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                if (index == 0) {
                    background = roundedRect("#1A2B5CE6", 14f)
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    currentInputConnection?.commitText("$word ", 1)
                }
            }
            suggestionStrip.addView(chip)
        }
    }

    private fun refreshEmojiMultiplierBar() {
        emojiMultiplierStrip.removeAllViews()
        listOf(1, 3, 5, 7).forEach { count ->
            val pill = TextView(this).apply {
                text = "$lastSelectedEmoji +$count"
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                background = roundedRect("#242429", 20f)
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    setMargins(dp(6), 0, dp(6), 0)
                }
                setOnClickListener {
                    currentInputConnection?.commitText(lastSelectedEmoji.repeat(count), 1)
                }
            }
            emojiMultiplierStrip.addView(pill)
        }
    }

    private fun showEmojiPanel() {
        togglePanelVisibility(showEmojiBar = true)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0E"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val emojis = listOf(
            "😂","🤣","😭","🥺","😍","🥰","😘","😊","😉","😌",
            "😔","🔥","✨","❤️","🫶","👍","🙏","🎉","🌹","👑",
            "😎","🤔","🙄","😴","🤍","💙","💜","🖤","💯","✅"
        )

        val grid = GridLayout(this).apply {
            columnCount = 7
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        emojis.forEach { em ->
            val tv = TextView(this).apply {
                text = em
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(44)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                setOnClickListener {
                    lastSelectedEmoji = em
                    refreshEmojiMultiplierBar()
                    currentInputConnection?.commitText(em, 1)
                }
            }
            grid.addView(tv)
        }

        layout.addView(grid)
        dynamicPanelContainer.removeAllViews()
        dynamicPanelContainer.addView(layout)
    }

    private fun showClipboardPanel() {
        togglePanelVisibility(showEmojiBar = false)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B0B0E")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val header = TextView(this).apply {
            text = "الحافظة 📋  (الأحدث والمثبتة)"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(header)

        recentClips.forEach { clip ->
            container.addView(createClipRow(clip, isPinned = false))
        }

        val pinnedHeader = TextView(this).apply {
            text = "المثبتة 📌"
            setTextColor(Color.parseColor("#8E8E98"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(4))
        }
        container.addView(pinnedHeader)

        pinnedClips.forEach { clip ->
            container.addView(createClipRow(clip, isPinned = true))
        }

        scroll.addView(container)
        dynamicPanelContainer.removeAllViews()
        dynamicPanelContainer.addView(scroll)
    }

    private fun createClipRow(text: String, isPinned: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect("#16161C", 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(6)) }

            val delBtn = TextView(context).apply {
                this.text = "🗑️"
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    if (isPinned) pinnedClips.remove(text) else recentClips.remove(text)
                    showClipboardPanel()
                }
            }

            val pinBtn = TextView(context).apply {
                this.text = if (isPinned) "📌" else "📍"
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    if (!isPinned) {
                        recentClips.remove(text)
                        pinnedClips.add(0, text)
                    } else {
                        pinnedClips.remove(text)
                        recentClips.add(0, text)
                    }
                    showClipboardPanel()
                }
            }

            val contentTv = TextView(context).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentInputConnection?.commitText(text, 1)
                    closePanels()
                }
            }

            addView(delBtn)
            addView(pinBtn)
            addView(contentTv)
        }
    }

    private fun showTrackpadPanel() {
        togglePanelVisibility(showEmojiBar = false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0E"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val padArea = FrameLayout(this).apply {
            background = roundedRect("#1C1C24", 18f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, 0, 0, dp(8))
            }
            val hint = TextView(context).apply {
                text = "🖱️
حرّك إصبعك لتحريك المؤشر، أو اضغط مطولاً ثم اسحب لتحديد"
                setTextColor(Color.parseColor("#8E8E98"))
                textSize = 13f
                gravity = Gravity.CENTER
            }
            addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            var lastX = 0f
            setOnTouchListener { _, ev ->
                when (ev.action) {
                    android.view.MotionEvent.ACTION_DOWN -> lastX = ev.x
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = ev.x - lastX
                        if (kotlin.math.abs(dx) > 18f) {
                            onCursorMove(if (dx > 0) 1 else -1)
                            lastX = ev.x
                        }
                    }
                }
                true
            }
        }
        root.addView(padArea)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }

        val actions = listOf(
            "تحديد الكل" to { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) },
            "نسخ" to { currentInputConnection?.performContextMenuAction(android.R.id.copy) },
            "قص" to { currentInputConnection?.performContextMenuAction(android.R.id.cut) },
            "لصق" to { currentInputConnection?.performContextMenuAction(android.R.id.paste) },
            "⌨️" to { closePanels() }
        )

        actions.forEach { (label, act) ->
            val btn = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                background = roundedRect("#181820", 12f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { act() }
            }
            actionsRow.addView(btn)
        }

        root.addView(actionsRow)
        dynamicPanelContainer.removeAllViews()
        dynamicPanelContainer.addView(root)
    }

    // ─── شريط الترجمة الفورية بالذكاء الاصطناعي داخل الكيبورد ───
    private fun showTranslatePanel() {
        closePanels()
        suggestionStrip.removeAllViews()

        val input = EditText(this).apply {
            hint = "اكتب هنا ليترجم فوراً بـ Gemini..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            background = roundedRect("#1C1C24", 12f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        val sendBtn = TextView(this).apply {
            text = "ترجم للـ EN ↵"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            background = roundedRect("#2B5CE6", 12f)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            setOnClickListener {
                runGeminiTaskOnCurrentField(GeminiAiClient.TaskType.TRANSLATE_EN)
            }
        }

        suggestionStrip.addView(input)
        suggestionStrip.addView(sendBtn)
    }

    private fun showAllToolsPanel() {
        togglePanelVisibility(showEmojiBar = false)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B0B0E")) }
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val allTools = listOf(
            "تغيير اللغات" to "🌐", "التاريخ والوقت" to "📅", "الحاسبة" to "🧮", "12 حركة" to "ّ",
            "اليد الواحدة" to "📱", "الكتابة بالصوت" to "🎙️", "وضع التخفي" to "🕵️", "إيموجيات" to "😊",
            "تغيير الحجم" to "📐", "الملصقات و GIF" to "🖼️", "ملصقاتي" to "✨", "الأذكار" to "📿",
            "رسالة سرية" to "🔒", "إخفاء الشريط" to "👁️", "شريط آيفون" to "🍎", "أصدقاء البطولة" to "🏆",
            "عجلة الحظ" to "🎡", "كلمات المرور" to "🔑", "كيبورد مقسوم" to "👐", "كيبورد عائم" to "☁️",
            "لصق" to "📋", "كتم الصوت والاهتزاز" to "🔇", "سجل الإجراءات" to "⏱️", "لغات الكتابة" to "⌨️",
            "AI Keyboard" to "🤖", "اقرأ النص" to "🔊"
        )

        allTools.forEach { (title, icon) ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }

                val circle = TextView(context).apply {
                    text = icon
                    textSize = 20f
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#1C1C24"))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                }

                val label = TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, 0)
                }

                addView(circle)
                addView(label)
                setOnClickListener {
                    closePanels()
                    if (title == "AI Keyboard") showAiAssistantBar()
                }
            }
            grid.addView(itemLayout)
        }

        scroll.addView(grid)
        dynamicPanelContainer.removeAllViews()
        dynamicPanelContainer.addView(scroll)
    }

    private fun toggleZakhrafaMode() {
        closePanels()
        suggestionStrip.removeAllViews()
        ZakhrafaEngine.Style.values().forEach { style ->
            val isSelected = ZakhrafaEngine.currentStyle == style && ZakhrafaEngine.isZakhrafaEnabled
            val chip = TextView(this).apply {
                text = style.title
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                background = roundedRect(if (isSelected) "#2B5CE6" else "#242429", 14f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    ZakhrafaEngine.currentStyle = style
                    ZakhrafaEngine.isZakhrafaEnabled = (style != ZakhrafaEngine.Style.NONE)
                    toggleZakhrafaMode()
                }
            }
            suggestionStrip.addView(chip)
        }
    }

    private fun togglePanelVisibility(showEmojiBar: Boolean) {
        if (dynamicPanelContainer.visibility == View.VISIBLE) {
            closePanels()
        } else {
            keyboardCanvas.visibility = View.GONE
            dynamicPanelContainer.visibility = View.VISIBLE
            emojiMultiplierStrip.visibility = if (showEmojiBar) View.VISIBLE else View.GONE
            suggestionStrip.visibility = if (showEmojiBar) View.GONE else View.VISIBLE
        }
    }

    private fun closePanels() {
        dynamicPanelContainer.visibility = View.GONE
        emojiMultiplierStrip.visibility = View.GONE
        suggestionStrip.visibility = View.VISIBLE
        keyboardCanvas.visibility = View.VISIBLE
    }

    override fun onCharacterCommit(text: String) {
        val output = if (text.length == 1) ZakhrafaEngine.processChar(text[0]) else text
        currentInputConnection?.commitText(output, 1)
    }

    override fun onDelete(count: Int) {
        currentInputConnection?.deleteSurroundingText(count, 0)
    }

    override fun onEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
    }

    override fun onCursorMove(delta: Int) {
        val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    override fun onOpenEmojiPanel() {
        showEmojiPanel()
    }

    private fun setupClipboardListener() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val copied = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (!copied.isNullOrBlank() && !recentClips.contains(copied)) {
                recentClips.add(0, copied)
            }
        }
    }

    private fun roundedRect(hex: String, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
