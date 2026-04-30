package com.jarvis.ceotitan.understanding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class IntentResult(
    val intent: String,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val confidence: Float = 1.0f,
    val language: Language = Language.MIXED
)

enum class Language { BANGLA, ENGLISH, BANGLISH, MIXED }

enum class Intent {
    OPEN_APP, CLOSE_APP,
    CALL_CONTACT, SEND_MESSAGE,
    FLASHLIGHT_ON, FLASHLIGHT_OFF,
    VOLUME_UP, VOLUME_DOWN, MUTE, VIBRATE,
    GO_HOME, GO_BACK, RECENT_APPS,
    PLAY_PAUSE_MEDIA, NEXT_TRACK, PREV_TRACK,
    SET_ALARM, SET_TIMER,
    TAKE_PHOTO, OPEN_CAMERA,
    SEARCH_WEB, SEARCH_YOUTUBE,
    OPEN_SETTINGS, OPEN_WIFI, OPEN_BLUETOOTH, OPEN_BRIGHTNESS,
    LOCK_SCREEN,
    SCROLL_UP, SCROLL_DOWN,
    READ_NOTIFICATIONS,
    SAVE_NOTE, READ_NOTES,
    ASK_AI, CHAT_AI,
    WEATHER, TIME_DATE,
    BUSINESS_NOTE, REMIND_ME,
    UNKNOWN
}

@Singleton
class SpeechUnderstandingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val synonymMap = buildSynonymMap()
    private val slangMap = buildSlangMap()
    private val transliterationMap = buildTransliterationMap()
    private val intentPatterns = buildIntentPatterns()

    suspend fun understand(rawInput: String): IntentResult {
        var processed = rawInput

        processed = layer1_cleanNoise(processed)
        processed = layer2_fixTransliteration(processed)
        processed = layer3_fuzzyCorrect(processed)
        val language = detectLanguage(processed)
        val intentResult = layer4_detectIntent(processed, language)
        val contextualResult = layer5_contextUnderstand(intentResult, processed)
        return layer6_actionDecide(contextualResult, processed)
    }

    private fun layer1_cleanNoise(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[،,;]+"), " ")
    }

    private fun layer2_fixTransliteration(input: String): String {
        var result = input
        transliterationMap.forEach { (key, value) ->
            result = result.replace(Regex("\\b$key\\b", RegexOption.IGNORE_CASE), value)
        }
        slangMap.forEach { (key, value) ->
            result = result.replace(Regex("\\b$key\\b", RegexOption.IGNORE_CASE), value)
        }
        return result
    }

    private fun layer3_fuzzyCorrect(input: String): String {
        val words = input.split(" ")
        return words.joinToString(" ") { word ->
            findBestMatch(word, synonymMap.keys.toList()) ?: word
        }
    }

    private fun layer4_detectIntent(input: String, language: Language): IntentResult {
        var bestIntent = Intent.UNKNOWN
        var bestParams = mutableMapOf<String, String>()
        var bestConfidence = 0.0f

        for ((intentName, patterns) in intentPatterns) {
            for (pattern in patterns) {
                val (matched, params, score) = matchPattern(input, pattern)
                if (matched && score > bestConfidence) {
                    bestConfidence = score
                    bestIntent = intentName
                    bestParams = params.toMutableMap()
                }
            }
        }

        if (bestConfidence < 0.4f) {
            return IntentResult(Intent.ASK_AI.name, "chat", mapOf("query" to input), 0.3f, language)
        }

        return IntentResult(bestIntent.name, "", bestParams, bestConfidence, language)
    }

    private fun layer5_contextUnderstand(intent: IntentResult, rawInput: String): IntentResult {
        return intent
    }

    private fun layer6_actionDecide(intent: IntentResult, rawInput: String): IntentResult {
        return intent
    }

    private fun detectLanguage(input: String): Language {
        val hasBangla = input.any { it.code in 0x0980..0x09FF }
        val hasLatin = input.any { it.isLetter() && it.code < 0x0250 }
        return when {
            hasBangla && hasLatin -> Language.BANGLISH
            hasBangla -> Language.BANGLA
            else -> Language.ENGLISH
        }
    }

    private fun matchPattern(input: String, pattern: String): Triple<Boolean, Map<String, String>, Float> {
        val normalizedInput = normalizeForMatch(input)
        val normalizedPattern = normalizeForMatch(pattern)

        if (normalizedInput.contains(normalizedPattern)) {
            return Triple(true, emptyMap(), 0.95f)
        }

        val similarity = stringSimilarity(normalizedInput, normalizedPattern)
        if (similarity > 0.7f) {
            return Triple(true, emptyMap(), similarity)
        }

        val params = extractParams(input, pattern)
        if (params.isNotEmpty()) {
            return Triple(true, params, 0.85f)
        }

        return Triple(false, emptyMap(), 0f)
    }

    private fun normalizeForMatch(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9\\u0980-\\u09ff\\s]"), "").trim()
    }

    private fun extractParams(input: String, pattern: String): Map<String, String> {
        val params = mutableMapOf<String, String>()

        val contactPatterns = listOf(
            Regex("(?:call|phone|ফোন|কল)\\s+(.+?)(?:\\s+ke|কে|ko|কো)?\\s*$", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+(?:ke|কে|ko|কো)\\s+(?:call|phone|ফোন|কল)", RegexOption.IGNORE_CASE)
        )
        for (p in contactPatterns) {
            val match = p.find(input)
            if (match != null) params["contact"] = match.groupValues[1].trim()
        }

        val messagePatterns = listOf(
            Regex("(?:message|msg|মেসেজ)\\s+(.+?)\\s+(?:dao|দাও|send|pathao|পাঠাও)\\s*(.*)?", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+(?:ke|কে)\\s+(?:message|msg|মেসেজ)\\s+(.*)?", RegexOption.IGNORE_CASE)
        )
        for (p in messagePatterns) {
            val match = p.find(input)
            if (match != null) {
                params["contact"] = match.groupValues[1].trim()
                if (match.groupValues.size > 2) params["message"] = match.groupValues[2].trim()
            }
        }

        val appPatterns = listOf(
            Regex("(?:open|kholo|খোলো|start|চালু)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+(?:open|kholo|খোলো|chalu|চালু)", RegexOption.IGNORE_CASE)
        )
        for (p in appPatterns) {
            val match = p.find(input)
            if (match != null) params["app"] = match.groupValues[1].trim()
        }

        return params
    }

    private fun findBestMatch(word: String, candidates: List<String>): String? {
        if (word.length < 3) return null
        var bestMatch: String? = null
        var bestScore = 0.6f
        for (candidate in candidates) {
            val score = stringSimilarity(word.lowercase(), candidate.lowercase())
            if (score > bestScore) {
                bestScore = score
                bestMatch = synonymMap[candidate] ?: candidate
            }
        }
        return bestMatch
    }

    private fun stringSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val maxLen = max(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - distance.toFloat() / maxLen.toFloat()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length; val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (s1[i-1] == s2[j-1]) dp[i-1][j-1]
            else min(dp[i-1][j-1], min(dp[i-1][j], dp[i][j-1])) + 1
        }
        return dp[m][n]
    }

    private fun buildSynonymMap(): Map<String, String> = mapOf(
        "tors" to "flashlight", "tarch" to "flashlight", "torch" to "flashlight",
        "torsh" to "flashlight", "টর্চ" to "flashlight", "টস" to "flashlight",
        "yutub" to "youtube", "utub" to "youtube", "utube" to "youtube",
        "ইউটুব" to "youtube", "ইউটিউব" to "youtube",
        "hom" to "home", "হোম" to "home", "হোমে" to "home",
        "bak" to "back", "ব্যাক" to "back",
        "vibe" to "vibrate", "vibret" to "vibrate",
        "vol" to "volume", "volum" to "volume",
        "cam" to "camera", "kamera" to "camera", "ক্যামেরা" to "camera",
        "msg" to "message", "massage" to "message", "মেসেজ" to "message",
        "kol" to "call", "fon" to "call", "ফোন" to "call", "কল" to "call",
        "chotgpt" to "chatgpt", "chatjpt" to "chatgpt",
        "wtsap" to "whatsapp", "wattsapp" to "whatsapp", "wapp" to "whatsapp"
    )

    private fun buildSlangMap(): Map<String, String> = mapOf(
        "amma" to "mother", "abba" to "father", "bhai" to "brother",
        "apu" to "sister", "dost" to "friend", "dosto" to "friend",
        "shop" to "fan-zon", "fanzon" to "fan-zon",
        "jao" to "go", "dao" to "send", "kholo" to "open",
        "pathao" to "send", "nao" to "take", "dhoro" to "hold",
        "chalao" to "start", "bandh" to "stop", "off" to "turn off",
        "on" to "turn on", "dekhao" to "show", "bolo" to "say",
        "likho" to "write", "shunte" to "listen", "bolchi" to "saying",
        "ashtesi" to "coming", "jacchi" to "going"
    )

    private fun buildTransliterationMap(): Map<String, String> = mapOf(
        "ami" to "i", "tumi" to "you", "se" to "he/she",
        "kivabe" to "how", "kothay" to "where", "ki" to "what",
        "kobe" to "when", "ken" to "why", "kara" to "who",
        "boro" to "big", "choto" to "small", "valo" to "good",
        "kharap" to "bad", "thanda" to "cold", "gorom" to "hot",
        "jao" to "go", "aso" to "come", "dekho" to "see",
        "shono" to "listen", "bolo" to "speak", "likhoo" to "write",
        "poro" to "read", "khao" to "eat", "dao" to "give"
    )

    private fun buildIntentPatterns(): Map<Intent, List<String>> = mapOf(
        Intent.FLASHLIGHT_ON to listOf(
            "flashlight on", "torch on", "tors on", "tarch on", "light on",
            "টর্চ জ্বালাও", "টর্চ অন", "আলো জ্বালাও", "torch jwalao",
            "flashlight켜", "light on koro", "টস অন"
        ),
        Intent.FLASHLIGHT_OFF to listOf(
            "flashlight off", "torch off", "light off", "টর্চ বন্ধ",
            "টর্চ অফ", "আলো নিভাও", "torch bondho", "torch off koro"
        ),
        Intent.GO_HOME to listOf(
            "go home", "home", "হোমে যাও", "hom jao", "home ja",
            "main screen", "হোম", "go to home", "home screen",
            "main screen e jao"
        ),
        Intent.GO_BACK to listOf(
            "go back", "back", "ব্যাক", "ব্যাক যাও", "bak jao",
            "previous", "আগের পেজ"
        ),
        Intent.VOLUME_UP to listOf(
            "volume up", "sound up", "ভলিউম বাড়াও", "vol up",
            "louder", "জোরে করো", "volume baro"
        ),
        Intent.VOLUME_DOWN to listOf(
            "volume down", "sound down", "ভলিউম কমাও", "vol down",
            "quieter", "আস্তে করো", "volume kamo"
        ),
        Intent.MUTE to listOf(
            "mute", "silent", "সাইলেন্ট", "নীরব", "শব্দ বন্ধ",
            "sound off", "quiet mode"
        ),
        Intent.VIBRATE to listOf(
            "vibrate", "vibration mode", "ভাইব্রেট", "ভাইব মোড"
        ),
        Intent.CALL_CONTACT to listOf(
            "call", "phone", "ring", "কল দাও", "ফোন দাও",
            "ke call", "কে কল", "ke phone"
        ),
        Intent.SEND_MESSAGE to listOf(
            "send message", "send msg", "message dao", "মেসেজ দাও",
            "whatsapp message", "msg pathao", "মেসেজ পাঠাও",
            "ke message", "কে মেসেজ"
        ),
        Intent.OPEN_APP to listOf(
            "open", "kholo", "খোলো", "start", "launch", "চালু করো",
            "open app", "app kholo"
        ),
        Intent.TAKE_PHOTO to listOf(
            "take photo", "take picture", "photo tolo", "ছবি তোলো",
            "camera", "selfie", "shoot"
        ),
        Intent.SET_ALARM to listOf(
            "set alarm", "alarm set", "অ্যালার্ম দাও", "alarm dao",
            "wake me", "wake up at"
        ),
        Intent.SET_TIMER to listOf(
            "set timer", "timer", "টাইমার", "countdown"
        ),
        Intent.SEARCH_YOUTUBE to listOf(
            "search youtube", "youtube search", "youtube te khojo",
            "ইউটিউবে খোঁজো", "youtube kholo", "youtube open"
        ),
        Intent.SCROLL_DOWN to listOf(
            "scroll down", "niche jao", "নিচে নামাও", "down scroll",
            "নিচে যাও", "scroll karo"
        ),
        Intent.SCROLL_UP to listOf(
            "scroll up", "upore jao", "উপরে যাও", "up scroll"
        ),
        Intent.READ_NOTIFICATIONS to listOf(
            "notifications", "notif read", "নোটিফিকেশন পড়ো",
            "কী message এসেছে", "new messages"
        ),
        Intent.SAVE_NOTE to listOf(
            "save note", "note rakho", "নোট সেভ করো",
            "write down", "remember this", "মনে রাখো"
        ),
        Intent.OPEN_WIFI to listOf(
            "wifi settings", "wifi", "ওয়াইফাই", "internet settings"
        ),
        Intent.OPEN_BLUETOOTH to listOf(
            "bluetooth", "ব্লুটুথ", "bluetooth settings"
        ),
        Intent.OPEN_BRIGHTNESS to listOf(
            "brightness", "screen brightness", "উজ্জ্বলতা", "bright settings"
        ),
        Intent.LOCK_SCREEN to listOf(
            "lock screen", "screen lock", "লক করো", "phone lock"
        ),
        Intent.RECENT_APPS to listOf(
            "recent apps", "recent", "সাম্প্রতিক", "multitask",
            "app switcher", "recent screen"
        ),
        Intent.BUSINESS_NOTE to listOf(
            "fan-zon", "business note", "supplier", "customer",
            "ব্যবসা নোট", "বিজনেস", "sales note"
        ),
        Intent.WEATHER to listOf(
            "weather", "আবহাওয়া", "rain", "বৃষ্টি", "temperature",
            "তাপমাত্রা", "sky"
        ),
        Intent.TIME_DATE to listOf(
            "time", "date", "সময়", "তারিখ", "clock", "what time",
            "ki time", "ki date"
        )
    )
}
