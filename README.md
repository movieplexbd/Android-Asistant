# Nexus Assistant (v4.0 — Nexus Edition) 🚀

A high-performance, AI-driven personal Android assistant powered by **Gemini 1.5 Flash**.
Combines deep system control, multi-language voice command, smart routines and real-time
translation — all coordinated by a single **Master Controller** state machine.

---

## ✨ What's New in v4.0 (Nexus)

### 🧠 Upgraded Master Control
- **`MasterController` state machine** (`IDLE → LISTENING → THINKING → EXECUTING → SPEAKING`)
  drives every UI surface — main screen, floating bubble, notification — from a single
  source of truth.
- **Reactive UI** via Kotlin Flow / `lifecycleScope` — status & stats update live.
- **Resilient pipeline**: every step (STT → Gemini → action → TTS) recovers gracefully on
  failure and auto-resumes listening.

### 🎙 Master Voice & Automation Upgrades
- **Continuous, partial-result-aware speech recognition** (auto-restart loop).
- **Strict JSON contract** with the model — multi-step `routine_steps` and chained
  `next_step` (e.g. biometric_auth → send money) are first-class.
- **Common-name app resolution** ("open whatsapp", "open chrome") with package fallback.
- **Deep accessibility automation**: click-by-text (with parent walk), scroll, swipe by
  gesture, home / back / recents / notifications / quick settings.
- **Native system actions**: alarm/reminder via `AlarmClock`, volume up/down/mute,
  Wi-Fi / Bluetooth / location / battery panel shortcuts, camera launch.

### 🆕 3 New Headline Features
1. **Wake-Word Service** — `WakeWordService` constantly listens for *"Hey Nexus"* /
   *"Nexus"* / *"Ok Nexus"* and hands off to the main `ForegroundService` on detection.
2. **Smart Routines (`RoutineEngine`)** — chain multiple intents in one command
   ("Good morning routine" → open WiFi → set alarm → read calendar). Steps are spaced
   so the OS has time to settle UI state.
3. **Real-time Multi-language Translation (`TranslationManager`)** — translate any
   spoken text to Bangla / English / Hindi / Arabic / Spanish / French / German /
   Japanese / Chinese and speak it back in the target locale.

---

## 🛠 Tech Stack

- **Language**: Kotlin
- **AI**: `com.google.ai.client.generativeai:generativeai` (Gemini 1.5 Flash)
- **UI**: Material Design 3, ConstraintLayout, Lottie
- **Persistence**: Room + KSP
- **Concurrency**: Kotlin Coroutines + Flow
- **CI/CD**: GitHub Actions → unsigned debug APK artifact

---

## 📲 CI/CD — Debug APK from GitHub Actions

Every push to `main`/`master` (or manual `workflow_dispatch`) builds a debug APK and
uploads it as the artifact **`nexus-assistant-debug-apk`**.

**Where to download:**
GitHub → repo → **Actions** tab → latest *Android CI/CD* run → **Artifacts** section.

---

## 🗺️ Architecture (high-level)

```
┌──────────────────────────────────────────────────────────────┐
│                     MasterController                         │
│  (State + Stats StateFlow — single source of truth)          │
└──────────────▲────────────▲────────────▲────────────────────┘
               │            │            │
   ┌───────────┴───┐  ┌─────┴────┐  ┌───┴───────────┐
   │ MainActivity  │  │ Overlay  │  │ Notification  │
   └───────────────┘  └──────────┘  └───────────────┘

┌──────────────────────────────────────────────────────────────┐
│                  ForegroundService                            │
│  STT → PromptEngine → GeminiClient → JSON                     │
│        ├─ ActionExecutor (call/sms/open/alarm/settings)       │
│        ├─ RoutineEngine  (chained intents)                    │
│        └─ TranslationManager (Gemini + locale-aware TTS)      │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  WakeWordService  →  triggers ForegroundService on hotword   │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  AssistantAccessibilityService                               │
│  click-by-text · scroll · swipe gesture · global actions     │
└──────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started Locally

```bash
git clone https://github.com/movieplexbd/Android-Asistant.git
cd Android-Asistant
./gradlew assembleDebug
```

The Gemini API key is read from `BuildConfig.GEMINI_API_KEY` (set in `app/build.gradle`).

---

## 📄 License
MIT.
