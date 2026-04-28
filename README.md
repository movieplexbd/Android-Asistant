# Jarvis / Nexus Assistant — v4.3 (Chat Edition)

A high-performance, AI-driven personal Android assistant powered by **Google Gemini**.
Voice **and** text driven — when STT is missing, just type. Built for Bangla and English
mixed commands like *"ammu ke call koro"* (call mom).

---

## 📲 Download the latest APK

| Where | How |
|---|---|
| **GitHub Releases** | Open the **[Releases](../../releases)** tab and download the latest `jarvis-assistant-vX.X-buildN.apk`. |
| **CI artifact**     | **[Actions](../../actions)** → latest *Android CI/CD* run → **Artifacts** → `jarvis-assistant-release-apk`. |

Each push to `main` automatically builds, signs, and publishes a new release.

### After install

1. Open the app and grant the requested permissions (Microphone, Contacts, Phone, SMS, Notifications).
2. Tap **API Settings** and paste your free Gemini key from <https://aistudio.google.com/apikey>.
3. Tap **Save**, then **Start Assistant**. You can talk *or* type in the chat box.

---

## ✨ What's new in v4.3 (Chat Edition)

### 💬 Text Chat Interface
- Full chat-style input right on the home screen.
- Same brain as the voice path — `STT → Gemini → action → TTS` becomes
  `text → Gemini → action → TTS`.
- Chat history with `[time] YOU:` / `[time] JARVIS:` lines, long-press to copy.
- Works even when the device has **no Speech-to-Text engine installed** — a yellow
  banner appears with a one-tap link to install Google Speech Services.

### 📞 Smart Contact Resolver
- Speaks/types names instead of numbers: *"ammu ke call koro"*, *"call boss"*,
  *"send sms to bhai 'pouch6chi'"*.
- Knows Bangla **and** English aliases:
  - `mom` ← ma, amma, ammu, ammi, mommy, mother, mum, mama
  - `dad` ← baba, abba, abbu, abbi, father, papa, daddy
  - `brother` ← bhai, vai, bhaiya, bro
  - `sister` ← apu, apa, didi, sis
  - `boss`, `friend`, `wife`, `husband`, `son`, `daughter`, `uncle`, `aunt`…
- Looks up the matching contact in your phonebook (READ_CONTACTS required).
- Falls back to a clean error message if the contact isn't found, instead of crashing.

### 🐛 Powerful Bug Reporter
- One-tap **Export Full Bug Report** button collects:
  device + OS + app version, every permission status, voice stack health,
  the assistant's current state, last command/reply/action, last error detail,
  the rolling activity log, **and** a 250-line tail of system logcat.
- Saves to a file and opens the system share sheet (email, WhatsApp, Drive…).
- A global crash handler stores any uncaught exception to disk, so the next launch
  shows a popup with the full stack trace and a Copy button — no more silent crashes.

### ⚡ Quick Action Chips
- Tap-to-fire chips for *Call ammu*, *Open WhatsApp*, *Set alarm 7am*, *Volume up*,
  *Wifi settings*, etc. Same chat-pipeline, no typing required.

### 🛠 Bug fixes
- **Fixed broken `activity_main.xml`** — orphan attribute fragment was breaking the layout.
- Removed the **leaked Gemini API key** from source. The app now ships with no key and
  asks the user to paste theirs in Settings (still recoverable in CI via `GEMINI_API_KEY` secret).
- **Deduplicated CI workflow** — only `android_build.yml` builds + signs + releases now.
- **STT engine missing** is no longer a hard failure — chat path takes over and the
  user is shown a fix link.
- Added `<queries>` block so app/package lookups work on Android 11+ targeting older SDKs.

---

## 🛠 Tech stack

- **Language**: Kotlin
- **AI**: REST to `generativelanguage.googleapis.com` (no SDK needed)
- **UI**: Material Design 3, ConstraintLayout, Lottie
- **Persistence**: Room + KSP
- **Concurrency**: Kotlin Coroutines + StateFlow
- **CI/CD**: GitHub Actions → signed release APK + GitHub Release

---

## 🗺️ Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     MasterController                         │
│ (state, audio level, partial, command, reply, error, chat)   │
└──────────────▲────────────▲────────────▲────────────▲────────┘
               │            │            │            │
       ┌───────┴───┐  ┌─────┴────┐  ┌────┴─────┐  ┌──┴────────┐
       │MainActivity│  │ Overlay  │  │Notif bar │  │ ChatView  │
       └────────────┘  └──────────┘  └──────────┘  └───────────┘

┌──────────────────────────────────────────────────────────────┐
│                  ForegroundService                            │
│   ┌──── voice path:  STT ─────┐                               │
│   │                            ├─→ PromptEngine → Gemini      │
│   └──── text path:  ChatUI ────┘    │                          │
│                                     ▼                          │
│   JSON intent → ActionExecutor (call/sms/open/alarm/…)         │
│                  ├─ ContactResolver (ammu → phonebook)         │
│                  ├─ RoutineEngine  (chained intents)           │
│                  └─ TranslationManager (Gemini + locale TTS)   │
└──────────────────────────────────────────────────────────────┘
```

---

## 🐛 Reporting bugs

Tap **Export Full Bug Report** in the Debug Console card and share the file in a new
GitHub issue. The report contains everything maintainers need to reproduce.
