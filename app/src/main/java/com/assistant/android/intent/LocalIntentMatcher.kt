package com.assistant.android.intent

import org.json.JSONObject

/**
 * Pure on-device intent matcher — covers ~80 % of everyday commands without burning a Gemini call.
 *
 * Recognises both English and Bangla romanized patterns ("WhatsApp kholo", "ammu ke call koro",
 * "torch on koro", "screenshot nao"). Returns a JSONObject in the same shape Gemini would emit,
 * so the caller can dispatch it through the same pipeline.
 *
 * Returns null if no high-confidence pattern matches — in that case the caller falls back to
 * the SmartGeminiOrchestrator.
 */
object LocalIntentMatcher {

    private data class Pattern(
        val regex: Regex,
        val intent: String,
        /** Optional callback that fills "target" / "message" / "reply" from the regex match. */
        val build: (MatchResult, JSONObject) -> Unit = { _, _ -> }
    )

    private val patterns: List<Pattern> = listOf(
        // ─── Time / Date ────────────────────────────────────────────────────────
        Pattern(re("(?:what(?:'s| is)?\\s+the\\s+time|what\\s+time(?:\\s+is\\s+it)?|time(?:\\s+koto|\\s+kemon)?|koyta\\s+baje|koto\\s+baje)"),
            "info_time") { _, j -> j.put("reply", currentTimeReply()) },

        Pattern(re("(?:what(?:'s| is)?\\s+(?:the\\s+)?date|today(?:'s)?\\s+date|aaj\\s+(?:ki\\s+)?(?:tarikh|baar)|aj\\s+kotodin)"),
            "info_date") { _, j -> j.put("reply", currentDateReply()) },

        // ─── Camera ─────────────────────────────────────────────────────────────
        Pattern(re("(?:take\\s+(?:a\\s+)?selfie|selfie\\s+(?:tolo|nao|tul)|front\\s+camera|samner\\s+camera)"),
            "take_selfie") { _, j -> j.put("reply", "Taking a selfie now.") },

        Pattern(re("(?:take\\s+(?:a\\s+)?(?:photo|picture|pic|snap)|capture\\s+(?:photo|image)|photo\\s+(?:tol|nao|lao)|chobi\\s+(?:tol|nao))"),
            "take_photo") { _, j -> j.put("reply", "Capturing photo.") },

        Pattern(re("(?:record\\s+(?:a\\s+)?video|video\\s+(?:record|kor|nao))"),
            "record_video") { _, j -> j.put("reply", "Opening video recorder.") },

        Pattern(re("(?:open\\s+camera|camera\\s+(?:open|kholo|chalu))"),
            "open_camera") { _, j -> j.put("reply", "Opening camera.") },

        // ─── Flashlight ─────────────────────────────────────────────────────────
        Pattern(re("(?:flash(?:light)?|torch|batti)\\s*(?:on|chalu|jalao|jala)"),
            "flashlight_on") { _, j -> j.put("reply", "Flashlight on.") },

        Pattern(re("(?:flash(?:light)?|torch|batti)\\s*(?:off|bondho|nivao|niba)"),
            "flashlight_off") { _, j -> j.put("reply", "Flashlight off.") },

        Pattern(re("(?:toggle\\s+(?:flash|torch)|flash\\s+toggle)"),
            "flashlight_toggle") { _, j -> j.put("reply", "Toggling flashlight.") },

        // ─── Volume ─────────────────────────────────────────────────────────────
        Pattern(re("(?:volume\\s+up|increase\\s+volume|sound\\s+up|shobdo\\s+barao|awaj\\s+barao)"),
            "volume_up") { _, j -> j.put("reply", "Volume up.") },

        Pattern(re("(?:volume\\s+down|decrease\\s+volume|sound\\s+down|shobdo\\s+komao|awaj\\s+komao)"),
            "volume_down") { _, j -> j.put("reply", "Volume down.") },

        Pattern(re("(?:mute|silent|chup|chupchap)"),
            "mute") { _, j -> j.put("reply", "Muted.") },

        // ─── Wi-Fi / Bluetooth / Settings ───────────────────────────────────────
        Pattern(re("(?:open\\s+wifi(?:\\s+settings)?|wifi\\s+(?:settings|kholo|on|off))"),
            "open_wifi") { _, j -> j.put("reply", "Opening Wi-Fi settings.") },

        Pattern(re("(?:open\\s+bluetooth(?:\\s+settings)?|bluetooth\\s+(?:settings|kholo|on|off))"),
            "open_bluetooth") { _, j -> j.put("reply", "Opening Bluetooth settings.") },

        Pattern(re("(?:open\\s+settings|settings\\s+kholo)"),
            "open_settings") { _, j -> j.put("reply", "Opening settings.") },

        Pattern(re("(?:open\\s+location|gps\\s+settings|location\\s+kholo)"),
            "open_location") { _, j -> j.put("reply", "Opening location settings.") },

        Pattern(re("(?:battery|battery\\s+saver|battery\\s+kholo)"),
            "open_battery") { _, j -> j.put("reply", "Opening battery settings.") },

        // ─── Screen / Navigation ────────────────────────────────────────────────
        Pattern(re("(?:take\\s+(?:a\\s+)?screenshot|screen\\s*shot|screenshot\\s+(?:nao|lao|kor))"),
            "screenshot") { _, j -> j.put("reply", "Taking screenshot.") },

        Pattern(re("(?:lock\\s+(?:the\\s+)?screen|lock\\s+phone|phone\\s+lock|screen\\s+lock)"),
            "lock_screen") { _, j -> j.put("reply", "Locking screen.") },

        Pattern(re("(?:go\\s+home|home\\s+button|main\\s+screen|home\\s+jao)"),
            "home") { _, j -> j.put("reply", "Going home.") },

        Pattern(re("(?:go\\s+back|back\\s+button|piche\\s+jao|fire\\s+jao)"),
            "back") { _, j -> j.put("reply", "Going back.") },

        Pattern(re("(?:open\\s+recent\\s+apps|recent\\s+apps|recents)"),
            "recents") { _, j -> j.put("reply", "Opening recent apps.") },

        Pattern(re("(?:open\\s+notifications?|show\\s+notifications?|notification\\s+kholo)"),
            "notifications") { _, j -> j.put("reply", "Opening notifications.") },

        Pattern(re("(?:open\\s+quick\\s+settings|quick\\s+settings)"),
            "quick_settings") { _, j -> j.put("reply", "Opening quick settings.") },

        Pattern(re("(?:scroll\\s+down|niche\\s+scroll|niche\\s+jao)"),
            "scroll_down") { _, j -> j.put("reply", "Scrolling down.") },

        Pattern(re("(?:scroll\\s+up|upor\\s+scroll|upor\\s+jao)"),
            "scroll_up") { _, j -> j.put("reply", "Scrolling up.") },

        // ─── Read messages ──────────────────────────────────────────────────────
        Pattern(re("(?:read\\s+(?:my\\s+)?(?:last\\s+)?messages?|kon\\s+message|message\\s+poro|sesh\\s+message)"),
            "read_messages") { _, j -> j.put("reply", "Reading your last message.") },

        // ─── Open apps (must come before generic "open X") ──────────────────────
        Pattern(re("(?:open|launch|start|kholo|chalu\\s+koro|chalao)\\s+(whatsapp|youtube|facebook|messenger|instagram|chrome|browser|gmail|maps|spotify|telegram|twitter|x|tiktok|phone|dialer|camera|calendar|clock|alarm|contacts|calculator|drive|photos|gallery|play\\s*store|playstore|settings)"),
            "open_app") { m, j ->
                val app = m.groupValues[1].trim().lowercase().replace(Regex("\\s+"), "")
                j.put("target", app); j.put("reply", "Opening $app.")
            },

        Pattern(re("(whatsapp|youtube|facebook|messenger|instagram|chrome|gmail|maps|spotify|telegram|tiktok|camera|calendar|calculator)\\s+(?:open|kholo|chalu|chalao)"),
            "open_app") { m, j ->
                val app = m.groupValues[1].trim().lowercase()
                j.put("target", app); j.put("reply", "Opening $app.")
            },

        // ─── Phone calls (after open_app so "call ammu" doesn't try to open "ammu") ─
        Pattern(re("(?:call|dial|phone)\\s+(?:to\\s+)?([a-zA-Z][a-zA-Z\\s\\.]{1,30})"),
            "call") { m, j ->
                val name = m.groupValues[1].trim().trimEnd('.', ',', ';')
                j.put("target", name); j.put("reply", "Calling $name.")
            },

        Pattern(re("([a-zA-Z][a-zA-Z\\s]{1,30}?)\\s+(?:ke|ko)\\s+(?:call|phone)\\s+(?:koro|dao|de|kor)"),
            "call") { m, j ->
                val name = m.groupValues[1].trim()
                j.put("target", name); j.put("reply", "Calling $name.")
            },

        Pattern(re("(?:call|dial)\\s+(\\+?\\d[\\d\\s\\-]{6,15})"),
            "call") { m, j ->
                val num = m.groupValues[1].replace(Regex("[\\s\\-]"), "")
                j.put("target", num); j.put("reply", "Dialing $num.")
            },

        // ─── Greetings & small talk (no API call needed) ───────────────────────
        Pattern(re("(?:hi|hello|hey|salam|assalamu\\s*alaikum|jarvis|nexus)\\b"),
            "answer") { _, j -> j.put("reply", randomGreeting()) },

        Pattern(re("(?:thank\\s*(?:you|s)|thanks|dhonnobad|thanx|ty)"),
            "answer") { _, j -> j.put("reply", "You're welcome.") },

        Pattern(re("(?:ke\\s+tumi|who\\s+are\\s+you|tumi\\s+ke|what(?:'s| is)?\\s+your\\s+name)"),
            "answer") { _, j -> j.put("reply", "I'm Jarvis, your personal Nexus assistant.") },

        Pattern(re("(?:bye|goodbye|good\\s*night|shubh\\s*ratri|ghum\\s+ja|sleep)"),
            "answer") { _, j -> j.put("reply", "Goodbye, see you soon.") },

        // ─── v4.4 NEW on-device features (zero API cost) ───────────────────────
        Pattern(re("(?:device\\s*info|device\\s*status|battery\\s+(?:status|kemon|koto|level|percent)|battery\\??$|ram\\s*(?:status|usage)?|storage\\s*(?:status|free)?|phone\\s*status)"),
            "device_info") { _, j -> j.put("reply", "Checking your device.") },

        Pattern(re("(?:save|add|note|likho|likhe\\s+rakho)\\s*(?:a\\s+)?note[:\\s]+(.+)"),
            "note_add") { m, j ->
                val text = m.groupValues[1].trim()
                j.put("message", text); j.put("reply", "Noted.")
            },

        Pattern(re("(?:note\\s+(?:kor|likho|rakho)|likhe\\s+rakho)[:\\s]+(.+)"),
            "note_add") { m, j ->
                val text = m.groupValues[1].trim()
                j.put("message", text); j.put("reply", "Noted.")
            },

        Pattern(re("(?:show|read|list|dekhao|porho)\\s+(?:my\\s+|amar\\s+)?notes?"),
            "note_read") { _, j -> j.put("reply", "Reading your notes.") },

        // Read recent WhatsApp / Messenger / Telegram messages (no API)
        Pattern(re("(?:read|show|porho|dekhao)\\s+(?:my\\s+)?(?:last\\s+|latest\\s+|recent\\s+)?(?:whatsapp\\s+|messenger\\s+|telegram\\s+)?messages?"),
            "read_messages") { _, j -> j.put("reply", "Here are your recent messages.") }
    )

    fun match(command: String): JSONObject? {
        val text = command.trim().lowercase()
        if (text.isEmpty()) return null
        for (p in patterns) {
            val m = p.regex.find(text) ?: continue
            val out = JSONObject().apply {
                put("intent", p.intent)
                put("language", "en-US")
            }
            p.build(m, out)
            if (!out.has("reply")) out.put("reply", "On it.")
            return out
        }
        return null
    }

    private fun re(pattern: String): Regex = Regex(pattern, RegexOption.IGNORE_CASE)

    private fun currentTimeReply(): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return "It's ${sdf.format(java.util.Date())}."
    }

    private fun currentDateReply(): String {
        val sdf = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
        return "Today is ${sdf.format(java.util.Date())}."
    }

    private val greetings = listOf(
        "Yes, how can I help you?",
        "I'm here, what do you need?",
        "Hello! Ready when you are.",
        "Hi! What can I do for you?"
    )
    private fun randomGreeting() = greetings.random()
}
