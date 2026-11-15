package com.pro.arabickeyboard.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiAiClient {

    private const val PREFS_NAME = "keyboard_pro_ai_prefs"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
    private const val KEY_PERSONA = "user_persona"
    private const val KEY_AUTO_PROOFREAD = "auto_proofread"
    private const val KEY_PRO_ACTIVATED = "pro_vip_activated"
    private const val KEY_PRO_PLAN = "pro_vip_plan"
    private const val KEY_THEME_BG = "theme_bg_hex"
    private const val KEY_THEME_KEY = "theme_key_hex"
    private const val KEY_THEME_ACCENT = "theme_accent_hex"

    // ─── إدارة تفعيل البرو مجاناً بضغطة واحدة (Fake Instant VIP) ───
    fun setProActivated(context: Context, activated: Boolean, planName: String = "مدى الحياة") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRO_ACTIVATED, activated)
            .putString(KEY_PRO_PLAN, planName)
            .apply()
    }

    fun isProActivated(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO_ACTIVATED, true) // مفعل افتراضياً أو عبر الشاشة
    }

    fun getProPlan(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRO_PLAN, "مدى الحياة 👑") ?: "مدى الحياة 👑"
    }

    // ─── حفظ وتطبيق الثيمات المختارة فوراً على الكيبورد ───
    fun saveActiveTheme(context: Context, bgHex: Long, keyHex: Long, accentHex: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_THEME_BG, bgHex)
            .putLong(KEY_THEME_KEY, keyHex)
            .putLong(KEY_THEME_ACCENT, accentHex)
            .apply()
    }

    fun getActiveTheme(context: Context): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bg = prefs.getLong(KEY_THEME_BG, 0xFF0B0B0E).toInt()
        val key = prefs.getLong(KEY_THEME_KEY, 0xFF242429).toInt()
        val accent = prefs.getLong(KEY_THEME_ACCENT, 0xFF2B5CE6).toInt()
        return Triple(bg, key, accent)
    }

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API, key.trim()).apply()
    }

    fun getApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API, "") ?: ""
    }

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_API).apply()
    }

    fun saveModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL, model).apply()
    }

    fun getModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, "gemini-flash-latest") ?: "gemini-flash-latest"
    }

    fun savePersona(context: Context, persona: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PERSONA, persona).apply()
    }

    fun getPersona(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PERSONA, "أكتب بالعربية، أحب الردود القصيرة والواضحة") ?: ""
    }

    fun setAutoProofread(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_PROOFREAD, enabled).apply()
    }

    fun isAutoProofread(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PROOFREAD, false)
    }

    enum class TaskType(val instruction: String) {
        PROOFREAD("صحح الأخطاء الإملائية والنحوية في النص التالي مع الحفاظ على نفس اللهجة والمعنى، وأرجع النص المصحح فقط بدون أي شرح إضافي:"),
        REPHRASE("أعد صياغة النص التالي بأسلوب احترافي وجذاب، وأرجع النص الجديد فقط بدون أي مقدمات:"),
        TRANSLATE_EN("ترجم النص التالي إلى اللغة الإنجليزية بدقة، وأرجع الترجمة فقط بدون أي نص إضافي:"),
        TRANSLATE_AR("ترجم النص التالي إلى اللغة العربية بدقة، وأرجع الترجمة فقط بدون أي نص إضافي:"),
        SMART_REPLY("اقترح رداً ذكياً ومناسباً ومختصراً على الرسالة التالية، وأرجع الرد فقط:")
    }

    suspend fun processText(
        context: Context,
        inputText: String,
        task: TaskType
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("يرجى إضافة مفتاح Gemini API من إعدادات AI Keyboard أولاً"))
        }
        if (inputText.isBlank()) {
            return@withContext Result.failure(Exception("اكتب نصاً أولاً أو حدده ليعمل المساعد الذكي"))
        }

        val model = getModel(context)
        val persona = getPersona(context)
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
            }

            val fullPrompt = buildString {
                if (persona.isNotBlank()) {
                    append("نبذة عن أسلوب المستخدم: $persona\n")
                }
                append("${task.instruction}\n\n$inputText")
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", fullPrompt)
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 256)
                })
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseStr = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                return@withContext Result.failure(Exception("خطأ من الخادم ($code)"))
            }

            val json = JSONObject(responseStr)
            val candidates = json.optJSONArray("candidates")
            val firstPart = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
            val resultText = firstPart?.optString("text")?.trim()

            if (!resultText.isNullOrEmpty()) {
                Result.success(resultText)
            } else {
                Result.failure(Exception("لم يتم استلام نص من النموذج"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
