package com.pro.arabickeyboard.engine

object ZakhrafaEngine {

    enum class Style(val title: String) {
        NONE("عادي"),
        MADD("مـمـدود"),
        MASHKOOL("مَشْكُول"),
        ROYAL("مُزَخْرَف 👑"),
        FANCY_EN("Fancy EN ✨")
    }

    var currentStyle: Style = Style.NONE
    var isZakhrafaEnabled: Boolean = false

    private val royalMap = mapOf(
        'ا' to "آ", 'ب' to "بـٌ", 'ت' to "تـٌ", 'ث' to "ثـٌ",
        'ج' to "جـّ", 'ح' to "حـً", 'خ' to "خـّ", 'د' to "دِ",
        'ذ' to "ذِ", 'ر' to "رٍ", 'ز' to "زٍ", 'س' to "سـّ",
        'ش' to "شـّ", 'ص' to "صـّ", 'ض' to "ضـّ", 'ط' to "طـّ",
        'ظ' to "ظـّ", 'ع' to "عـْ", 'غ' to "غـّ", 'ف' to "فـّ",
        'ق' to "قـّ", 'ك' to "كـّ", 'ل' to "لـِ", 'م' to "مـْ",
        'ن' to "نـً", 'ه' to "هـْ", 'و' to "ؤ", 'ي' to "يـﮯ"
    )

    private val tashkeelMarks = listOf("َ", "ُ", "ِ", "ْ", "ّ")

    private val connectableArabic = setOf(
        'ب','ت','ث','ج','ح','خ','س','ش','ص','ض','ط','ظ','ع','غ','ف','ق','ك','ل','م','ن','ه','ي'
    )

    private val fancyEnMap = mapOf(
        'a' to "𝒶", 'b' to "𝒷", 'c' to "𝒸", 'd' to "𝒹", 'e' to "ℯ",
        'f' to "𝒻", 'g' to "ℊ", 'h' to "𝒽", 'i' to "𝒾", 'j' to "𝒿",
        'k' to "𝓀", 'l' to "𝓁", 'm' to "𝓂", 'n' to "𝓃", 'o' to "ℴ",
        'p' to "𝓅", 'q' to "𝓆", 'r' to "𝓇", 's' to "𝓈", 't' to "𝓉",
        'u' to "𝓊", 'v' to "𝓋", 'w' to "𝓌", 'x' to "𝓍", 'y' to "𝓎", 'z' to "𝓏"
    )

    fun processChar(char: Char): String {
        if (!isZakhrafaEnabled || currentStyle == Style.NONE) return char.toString()
        return when (currentStyle) {
            Style.MADD -> if (char in connectableArabic) "${char}ـ" else char.toString()
            Style.MASHKOOL -> if (char.isLetter()) "$char${tashkeelMarks.random()}" else char.toString()
            Style.ROYAL -> royalMap[char] ?: char.toString()
            Style.FANCY_EN -> fancyEnMap[char.lowercaseChar()] ?: char.toString()
            else -> char.toString()
        }
    }

    fun transformWord(word: String, style: Style): String {
        if (word.isBlank()) return word
        return word.map { ch ->
            when (style) {
                Style.MADD -> if (ch in connectableArabic) "${ch}ـ" else ch.toString()
                Style.MASHKOOL -> if (ch.isLetter()) "$ch${tashkeelMarks.random()}" else ch.toString()
                Style.ROYAL -> royalMap[ch] ?: ch.toString()
                Style.FANCY_EN -> fancyEnMap[ch.lowercaseChar()] ?: ch.toString()
                else -> ch.toString()
            }
        }.joinToString("")
    }
}
