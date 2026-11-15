package com.pro.arabickeyboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pro.arabickeyboard.ai.GeminiAiClient
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KeyboardProAppReplica()
            }
        }
    }
}

val BgDark = Color(0xFF0B0B0E)
val CardDark = Color(0xFF15151B)
val CardBorder = Color(0xFF252530)
val PrimaryBlue = Color(0xFF2B5CE6)
val GoldAccent = Color(0xFFD4AF37)
val TextSecondary = Color(0xFF8E8E98)

enum class BottomNavTab(val title: String, val icon: String) {
    HOME("الرئيسية", "🏠"),
    SETTINGS("الإعدادات", "⚙️"),
    REQUESTS("الطلبات", "❓"),
    DEVELOPER("المطور", "👤")
}

enum class SubScreen {
    NONE, WHATS_NEW, AI_KEYBOARD, PRO_SUBSCRIPTION
}

data class KeyboardThemeModel(
    val name: String,
    val bgHex: Long,
    val keyHex: Long,
    val accentHex: Long,
    val isNew: Boolean = false,
    val isPro: Boolean = false,
    val author: String? = null,
    val downloads: Int? = null
)

@Composable
fun KeyboardProAppReplica() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(BottomNavTab.HOME) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    var showTestBox by remember { mutableStateOf(true) }
    var testInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        when (currentSubScreen) {
            SubScreen.PRO_SUBSCRIPTION -> ProSubscriptionScreen(onBack = { currentSubScreen = SubScreen.NONE })
            SubScreen.AI_KEYBOARD -> AiKeyboardSettingsScreen(onBack = { currentSubScreen = SubScreen.NONE })
            SubScreen.WHATS_NEW -> WhatsNewScreen(onBack = { currentSubScreen = SubScreen.NONE })
            SubScreen.NONE -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopHeaderBar(
                        title = when (currentTab) {
                            BottomNavTab.HOME -> "كيبورد برو"
                            BottomNavTab.SETTINGS -> "الإعدادات"
                            BottomNavTab.REQUESTS -> "الطلبات والاقتراحات"
                            BottomNavTab.DEVELOPER -> "حول المطور"
                        },
                        onCrownClick = { currentSubScreen = SubScreen.PRO_SUBSCRIPTION },
                        onPickerClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showInputMethodPicker()
                        }
                    )

                    if (showTestBox) {
                        OutlinedTextField(
                            value = testInput,
                            onValueChange = { testInput = it },
                            placeholder = { Text("اكتب هنا للتجربة...", color = TextSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardDark,
                                unfocusedContainerColor = CardDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = CardBorder
                            )
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            BottomNavTab.HOME -> HomeThemesScreen()
                            BottomNavTab.SETTINGS -> SettingsListScreen(
                                onOpenWhatsNew = { currentSubScreen = SubScreen.WHATS_NEW },
                                onOpenAiKeyboard = { currentSubScreen = SubScreen.AI_KEYBOARD }
                            )
                            BottomNavTab.REQUESTS -> RequestsScreen()
                            BottomNavTab.DEVELOPER -> DeveloperAboutScreen()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 96.dp)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .clickable { showTestBox = !showTestBox },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⌨️", fontSize = 22.sp)
                }

                FloatingBottomBar(
                    selectedTab = currentTab,
                    onSelect = { currentTab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
fun TopHeaderBar(title: String, onCrownClick: () -> Unit, onPickerClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CardDark)
                .border(1.dp, CardBorder, CircleShape)
                .clickable { onPickerClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("⌨️", fontSize = 18.sp)
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF261F10))
                .border(1.dp, GoldAccent.copy(alpha = 0.5f), CircleShape)
                .clickable { onCrownClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("👑", fontSize = 18.sp)
        }
    }
}

@Composable
fun HomeThemesScreen() {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("الثيمات") }

    val officialThemes = listOf(
        KeyboardThemeModel("أخضر", 0xFF0A1F0A, 0xFF153815, 0xFF2ECC71, isNew = true),
        KeyboardThemeModel("ميكانيكي ليلي ذهبي", 0xFF141418, 0xFF222228, 0xFFD4AF37, isNew = true, isPro = true),
        KeyboardThemeModel("آيفون نهاري", 0xFFD1D5DB, 0xFFFFFFFF, 0xFF2B5CE6),
        KeyboardThemeModel("آيفون ليلي", 0xFF1C1C1E, 0xFF3A3A3C, 0xFF2B5CE6),
        KeyboardThemeModel("iOS-27", 0xFF0F1319, 0xFF1E2631, 0xFF3B82F6, isPro = true),
        KeyboardThemeModel("أوركل ليلي", 0xFF0D1B1E, 0xFF162C30, 0xFF10B981),
        KeyboardThemeModel("ألوان اليوم", 0xFF1E1B2E, 0xFF2D2844, 0xFF8B5CF6, isPro = true),
        KeyboardThemeModel("طبيعة اليوم", 0xFF0F2419, 0xFF193827, 0xFF34D399),
        KeyboardThemeModel("ماتريال غامق", 0xFF1E1A1D, 0xFF332C30, 0xFFF472B6),
        KeyboardThemeModel("شطرنج", 0xFF121212, 0xFF2A2A2A, 0xFFEF4444, isPro = true),
        KeyboardThemeModel("ماتريال مميز فاتح", 0xFFFEF3C7, 0xFFFFFFFF, 0xFFD97706),
        KeyboardThemeModel("بنفسجي زجاجي", 0xFF1D1427, 0xFF2E203D, 0xFFA855F7, isPro = true)
    )

    val communityThemes = listOf(
        KeyboardThemeModel("آيفون شفاف", 0xFF141418, 0xFF25252C, 0xFF3B82F6, author = "ToFitLdn", downloads = 67),
        KeyboardThemeModel("أساسي أبيض", 0xFFE5E7EB, 0xFFFFFFFF, 0xFF1F2937, author = "منظم", downloads = 17),
        KeyboardThemeModel("Royal Brown", 0xFF271F16, 0xFF3D3122, 0xFFD4AF37, author = "Bezzo", downloads = 12),
        KeyboardThemeModel("زجاجي برو", 0xFF0D1322, 0xFF19233C, 0xFF3B82F6, author = "يوسف النمر", downloads = 12),
        KeyboardThemeModel("لهب بنفسجي", 0xFF1A0F24, 0xFF2D193D, 0xFFEC4899, author = "سيمو - SEMO", downloads = 16),
        KeyboardThemeModel("الزمرد الملكي", 0xFF071F18, 0xFF11382D, 0xFF10B981, author = "سيمو - SEMO", downloads = 39)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141829))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("الودجت العربي", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("زيّن شاشتك بالعربي.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ودجت عربية بتصاميم أنيقة ودعم كامل للقرآن والأذكار", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث عن ثيم بالاسم أو اللون...", color = TextSecondary, fontSize = 13.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("الثيمات", "برو", "ثيماتي", "المستخدمين").forEach { tab ->
                val selected = activeCategory == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) PrimaryBlue else Color.Transparent)
                        .clickable { activeCategory = tab },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val displayedList = when (activeCategory) {
            "المستخدمين" -> communityThemes
            "برو" -> officialThemes.filter { it.isPro }
            else -> officialThemes
        }.filter { it.name.contains(searchQuery) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(displayedList) { theme ->
                ThemePreviewCard(theme) {
                    GeminiAiClient.saveActiveTheme(context, theme.bgHex, theme.keyHex, theme.accentHex)
                    Toast.makeText(context, "تم تطبيق ثيم «${theme.name}» بنجاح 🎨", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun ThemePreviewCard(theme: KeyboardThemeModel, onApply: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (theme.isPro) GoldAccent.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(18.dp))
            .clickable { onApply() }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.bgHex))
                    .padding(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        listOf("خ", "ح", "ج", "ه", "ع"),
                        listOf("ك", "م", "ن", "ت", "ا"),
                        listOf("⌫", "د", "ظ", "ز", "و")
                    ).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            row.forEach { char ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(theme.keyHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, color = Color.White, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
                if (theme.isPro) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF261F10))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("👑 PRO", color = GoldAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(theme.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            if (theme.author != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("بواسطة ${theme.author}", color = TextSecondary, fontSize = 10.sp)
                    Text("⬇ ${theme.downloads}", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsListScreen(onOpenWhatsNew: () -> Unit, onOpenAiKeyboard: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingRowCard("ما الجديد", "أهم الإضافات في آخر تحديث", "⭐", Color(0xFFF59E0B), onClick = onOpenWhatsNew)
        SettingRowCard("AI Keyboard", "صلّح وأعد الصياغة وترجم بمفتاح ذكاء اصطناعي مجاني منك", "✨", Color(0xFF3B82F6), onClick = onOpenAiKeyboard)
        SettingRowCard("اللغات والتخطيط", "لغات الكتابة، الأرقام، تخطيط آيفون", "⌨️", Color(0xFF3B82F6))
        SettingRowCard("الكتابة الذكية", "الاقتراحات، التصحيح التلقائي، التوقع", "🅰️", Color(0xFF10B981))
        SettingRowCard("القاموس الشخصي", "الكلمات التي تعلمها الكيبورد منك", "📖", Color(0xFF8B5CF6))
        SettingRowCard("الاختصارات", "عبارات كاملة تكتبها بكلمة قصيرة", "⚡", Color(0xFFF59E0B))
        SettingRowCard("المظهر", "الخلفية، شكل الأزرار، الحجم، شريط الأدوات", "🎨", Color(0xFFEC4899))
        SettingRowCard("الزخرفة", "زخرفة الكلام وأنت تكتب، عربي وإنجليزي", "✨", Color(0xFFA855F7))
        SettingRowCard("الحافظة", "سجل النسخ واللصق", "📋", Color(0xFF0EA5E9))
        SettingRowCard("Keyboard Pass", "حساباتك وبطاقاتك والأيبان، تكتبها بالبصمة", "🔑", Color(0xFF10B981))
    }
}

@Composable
fun SettingRowCard(title: String, subtitle: String, iconText: String, badgeColor: Color, onClick: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, color = badgeColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
            Text("‹", color = TextSecondary, fontSize = 20.sp)
        }
    }
}

@Composable
fun AiKeyboardSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var savedKey by remember { mutableStateOf(GeminiAiClient.getApiKey(context)) }
    var apiKeyInput by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(GeminiAiClient.getModel(context)) }
    var personaPrompt by remember { mutableStateOf(GeminiAiClient.getPersona(context)) }
    var autoProofread by remember { mutableStateOf(GeminiAiClient.isAutoProofread(context)) }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    val availableModels = listOf("gemini-flash-latest", "gemini-2.0-flash", "gemini-1.5-pro")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ رجوع", color = PrimaryBlue, fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            Spacer(modifier = Modifier.weight(1f))
            Text("AI Keyboard", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            "يصلح ما تكتبه ويعيد صياغته ويترجمه ويشرح الردود من زر AI في شريط الكيبورد. يعمل بمفتاحك أنت من Google Gemini مباشرة.",
            color = TextSecondary,
            fontSize = 12.sp
        )

        SettingRowCard(
            title = "احصل على مفتاح مجاني",
            subtitle = "يفتح صفحة المفاتيح في Google AI Studio، انسخ المفتاح والصقه هنا",
            iconText = "🔑",
            badgeColor = PrimaryBlue,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
            }
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val masked = if (savedKey.length > 8) "${savedKey.take(4)}••••••${savedKey.takeLast(4)}" else if (savedKey.isNotEmpty()) "محفوظ ✔" else "غير مضاف بعد"
                Text("المفتاح المحفوظ: $masked", color = if (savedKey.isNotEmpty()) Color(0xFF10B981) else TextSecondary, fontSize = 13.sp)

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text("الصق المفتاح هنا (AIzaSy...)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            GeminiAiClient.saveApiKey(context, apiKeyInput)
                            savedKey = GeminiAiClient.getApiKey(context)
                            apiKeyInput = ""
                            Toast.makeText(context, "تم حفظ مفتاح Gemini بنجاح ✔", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("حفظ المفتاح", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        GeminiAiClient.clearApiKey(context)
                        savedKey = ""
                        Toast.makeText(context, "تم حذف المفتاح", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("حذف المفتاح", color = Color(0xFFFF6B6B))
                }
            }
        }

        Text("| النموذج", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availableModels.forEach { modelName ->
                val isSelected = selectedModel == modelName
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) PrimaryBlue else CardDark)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            selectedModel = modelName
                            GeminiAiClient.saveModel(context, modelName)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(modelName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("| يتعلم منك", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        OutlinedTextField(
            value = personaPrompt,
            onValueChange = {
                personaPrompt = it
                GeminiAiClient.savePersona(context, it)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("تدقيق تلقائي أثناء الكتابة", color = Color.White, fontSize = 14.sp)
            Switch(
                checked = autoProofread,
                onCheckedChange = {
                    autoProofread = it
                    GeminiAiClient.setAutoProofread(context, it)
                }
            )
        }

        Button(
            onClick = {
                isTesting = true
                scope.launch {
                    val res = GeminiAiClient.processText(
                        context,
                        "اهلا يسطا انا بجرب كيبورد برو بالذكاء الاصطناعي",
                        GeminiAiClient.TaskType.REPHRASE
                    )
                    isTesting = false
                    testResult = res.getOrElse { "خطأ: ${it.message}" }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isTesting) "جاري الاتصال بـ Gemini..." else "جرّب المساعد ✨", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (testResult.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "رد المساعد:\n$testResult",
                    color = GoldAccent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ما الجديد ⭐", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        SettingRowCard("AI Keyboard بـ Gemini", "تصحيح إملائي وإعادة صياغة وترجمة فورية داخل الكيبورد.", "✨", PrimaryBlue)
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("تمام", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RequestsScreen() {
    var details by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            placeholder = { Text("ما الميزة التي تريدها؟ وكيف ستفيدك؟", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            shape = RoundedCornerShape(16.dp)
        )
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("إرسال الطلب", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeveloperAboutScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRowCard("كيبورد برو", "الإصدار 1.0.6 - نسخة Replica الكاملة", "⌨️", PrimaryBlue)
    }
}

// ─── شاشة الاشتراك الوهمية الفخمة: تفعيل فوري بضغطة واحدة بدون أي دفع ───
@Composable
fun ProSubscriptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isProActive by remember { mutableStateOf(GeminiAiClient.isProActivated(context)) }
    var activePlan by remember { mutableStateOf(GeminiAiClient.getProPlan(context)) }
    var selectedPlanIndex by remember { mutableStateOf(2) } // مدى الحياة افتراضياً

    val plans = listOf(
        Triple("شهري", "مجاناً (مفعل محلياً)", "يُحصّل كل شهر ويتجدد تلقائياً حتى تلغيه"),
        Triple("سنوي", "مجاناً (مفعل محلياً)", "يُحصّل مرة كل سنة ويتجدد تلقائياً حتى تلغيه"),
        Triple("مدى الحياة", "الأفضل قيمة 👑", "دفعة واحدة بلا تجديد ولا رسوم إضافية، كل المزايا للأبد")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ رجوع", color = TextSecondary, fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            Text("كيبورد برو Pro", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(40.dp))
        }

        // أيقونة التاج الذهبي الدائرية الكبيرة كما في الفيديو
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color(0xFF261F10))
                .border(2.dp, GoldAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("👑", fontSize = 40.sp)
        }

        if (isProActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14291D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("اشتراك Pro مفعل بالكامل!", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("الباقة الحالية: $activePlan — جميع الثيمات والميزات مفتوحة مجاناً.", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Text("افتح كل التصاميم والمزايا المميزة", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)

        listOf(
            "أنماط الإيموجي المميزة Fluent 3D وتويتر وجوجل، وخطوط الإيموجي من ملفاتك",
            "الخلفية الشفافة مع البلور ترى التطبيق خلف الكيبورد",
            "الثيمات والملصقات والمميزات الحصرية الجديدة فور إضافتها",
            "نسخة احتياطية يومية تلقائية على Google Drive واستعادتها على أي هاتف"
        ).forEach { perk ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("✔  ", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(perk, color = Color.White, fontSize = 13.sp)
            }
        }

        plans.forEachIndexed { idx, (title, badge, desc) ->
            val isSelected = selectedPlanIndex == idx
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF211C12) else CardDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) GoldAccent else CardBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        selectedPlanIndex = idx
                        GeminiAiClient.setProActivated(context, true, title)
                        isProActive = true
                        activePlan = title
                        Toast.makeText(context, "🎉 مبروك! تم تفعيل باقة «$title» مجاناً بالكامل!", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = if (isSelected) GoldAccent else Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, color = TextSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) GoldAccent else Color(0xFF252530))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = badge,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // زر التفعيل الفوري المجاني
        Button(
            onClick = {
                val chosenTitle = plans[selectedPlanIndex].first
                GeminiAiClient.setProActivated(context, true, chosenTitle)
                isProActive = true
                activePlan = chosenTitle
                Toast.makeText(context, "👑 تم تفعيل كيبورد برو Pro ($chosenTitle) بنجاح بدون أي رسوم!", Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = if (isProActive) "مفعل بالفعل 👑 (اضغط لتجديد التفعيل الفوري)" else "اشترك وفعّل مجاناً الآن 👑",
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }

        // زر استعادة الشراء كما في الثانية 0:49 من الفيديو
        OutlinedButton(
            onClick = {
                GeminiAiClient.setProActivated(context, true, "مدى الحياة 👑")
                isProActive = true
                activePlan = "مدى الحياة 👑"
                Toast.makeText(context, "✅ تم استعادة وتفعيل عضوية Pro مدى الحياة بنجاح!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Text("استعادة الشراء", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FloatingBottomBar(
    selectedTab: BottomNavTab,
    onSelect: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xF215151C))
            .border(1.dp, CardBorder, RoundedCornerShape(34.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomNavTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (isSelected) PrimaryBlue else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.icon, fontSize = 16.sp)
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
