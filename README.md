# Android Assistant

## Overview

Android Assistant is a production-level Android Voice Assistant application built with Kotlin. It leverages the Google Gemini AI for intelligent responses, integrates with Android's native Speech-to-Text (STT) and Text-to-Speech (TTS) capabilities, and features a robust memory system using Room Database. The application is designed to run in the background, listen for a wake word, understand voice commands, and execute various phone automation tasks.

## Features

- **Voice-based AI Assistant**: Interact with the assistant using natural voice commands.
- **Wake Word Detection**: Activates upon a customizable wake word (e.g., "Hey Assistant").
- **Continuous Background Service**: Optimized for low battery usage, ensuring the assistant is always ready.
- **Speech-to-Text (STT)**: Converts spoken commands into text for AI processing.
- **Text-to-Speech (TTS)**: Generates natural-sounding voice responses from the AI.
- **Gemini API Integration**: Utilizes the Gemini AI as the core intelligence for understanding context, generating responses, and converting commands into structured actions.
- **Action Execution System**: Automates various phone tasks.
- **Personal Memory System**: Stores and retrieves user-specific data for personalized interactions.

## System Requirements

### Core Features
- Voice-based AI assistant
- Wake word detection
- Continuous background service
- Speech-to-Text (STT)
- Text-to-Speech (TTS)
- Gemini API integration
- Action execution system
- Personal memory system

### Voice System
- **Input**: User speaks via microphone.
- **Processing**: Speech converted to text using Android `SpeechRecognizer`.
- **Output**: AI response using Android `TextToSpeech`.

### Gemini AI Integration
- **Intelligence**: Uses Gemini API to understand natural language, convert user commands into structured JSON actions, maintain conversation context, and utilize memory data.
- **Prompt Format**: Always returns responses in a strict JSON format:
  ```json
  {
    "intent": "call | open_app | message | reminder | info | automation | unknown",
    "target": "",
    "message": "",
    "time": "",
    "reply": ""
  }
  ```

### Memory System
Implemented using Room Database for local persistent storage. Key memory types include:
- **Contact Memory**: Stores name, phone number, and relationship.
- **User Preferences**: Stores favorite apps, habits, and settings.
- **History Memory**: Records past commands, AI responses, and timestamps.
- **Smart Context Memory**: Learns user behavior patterns and frequently used commands.

### Automation Engine
Executes real Android actions:
- Make phone calls
- Send SMS
- Open apps
- Toggle flashlight (placeholder)
- Control volume (placeholder)
- Open settings
- Set reminders/alarms (placeholder)

### Wake Word System
- Lightweight foreground service for continuous background listening.
- Activates full listening mode upon wake word detection, processes command, and returns to sleep mode.
- Optimized for battery efficiency.

### UI Requirements
- Modern, clean, and minimal design.
- Voice button and status indicator.
- Conversation display.
- Light theme only.

### Performance Requirements
- Low battery usage.
- Fast response time (<1.5 sec).
- Offline fallback for basic commands.
- Optimized background service.

### Permissions Required
- `RECORD_AUDIO`
- `INTERNET`
- `CALL_PHONE`
- `SEND_SMS`
- `FOREGROUND_SERVICE`
- `SYSTEM_ALERT_WINDOW` (for advanced automation, requires special handling)
- `ACCESSIBILITY_SERVICE` (for advanced control, requires special handling)

## App Architecture

The application follows a clean modular architecture:

```
/app
├── voice/
│    ├── SpeechRecognizerManager.kt
│    └── TTSManager.kt
├── ai/
│    ├── GeminiClient.kt
│    └── PromptEngine.kt
├── memory/
│    ├── RoomDatabase.kt
│    ├── MemoryRepository.kt
│    ├── dao/
│    │    ├── ContactDao.kt
│    │    ├── HistoryDao.kt
│    │    ├── PreferenceDao.kt
│    │    └── ContextDao.kt
│    └── entity/
│         ├── Contact.kt
│         ├── History.kt
│         ├── Preference.kt
│         └── ContextEntity.kt
├── automation/
│    └── ActionExecutor.kt
├── service/
│    ├── WakeWordService.kt
│    └── ForegroundService.kt
└── ui/
     └── MainActivity.kt
```

## System Flow

1.  **Wake word detected**
2.  Microphone activates
3.  Speech converted to text
4.  Text sent to Gemini API
5.  Gemini returns JSON intent
6.  Memory system checked
7.  Action executed on Android
8.  Voice response generated via TTS
9.  System returns to idle mode

## Setup and Installation

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/movieplexbd/Android-Asistant.git
    cd Android-Asistant
    ```

2.  **Open in Android Studio**: Open the `Android-Asistant` project in Android Studio.

3.  **Gemini API Key**: Obtain a Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey). Replace `
`"YOUR_GEMINI_API_KEY"` in `app/src/main/res/values/strings.xml` with your actual key.

4.  **Build and Run**: Build and run the project on an Android device or emulator.

## Contributing

We welcome contributions to the Android Assistant project! Please feel free to fork the repository, make your changes, and submit a pull request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Advanced Features and Future Enhancements

To make the Android Assistant more powerful, beautiful, and user-friendly, consider the following upgrades and enhancements:

### 1. Enhanced Wake Word Detection
- **Customizable Wake Words**: Allow users to record and train their own wake words for a more personalized experience.
- **On-Device Wake Word Model**: Integrate a lightweight, on-device wake word detection model (e.g., using TensorFlow Lite) to reduce latency and reliance on cloud services, improving privacy and offline capabilities.
- **Improved Battery Efficiency**: Further optimize the wake word service to consume even less power, potentially by dynamically adjusting microphone sensitivity or using hardware-accelerated wake word engines if available on the device.

### 2. Advanced Speech-to-Text (STT) and Text-to-Speech (TTS)
- **Offline STT/TTS**: Implement robust offline STT and TTS capabilities for basic commands and responses, ensuring functionality even without an internet connection.
- **Multi-language Support**: Expand language support beyond English and Bengali to include other widely spoken languages, leveraging Google's advanced STT/TTS models.
- **Voice Personalization**: Allow users to choose different AI voices or even train the TTS to mimic their own voice or a preferred voice.

### 3. Deeper Gemini AI Integration
- **Function Calling**: Fully leverage Gemini's function calling capabilities to directly invoke Android system APIs or custom app functions based on user intent, making the automation engine more dynamic and powerful.
- **Contextual Understanding**: Improve the prompt engineering to maintain even longer and more complex conversation contexts, leading to more natural and coherent interactions.
- **Proactive Suggestions**: Based on user habits and context memory, enable the AI to proactively suggest actions or information before the user explicitly asks.

### 4. Robust Memory System
- **Semantic Search for Memory**: Implement semantic search over the Room Database to retrieve relevant memories based on meaning, not just keywords, enhancing the AI's ability to recall past interactions.
- **Cross-Device Sync**: Offer an optional cloud synchronization for memory data (e.g., using Firebase or a custom backend) to provide a consistent experience across multiple devices.
- **Privacy Controls**: Provide granular control to users over what data is stored in memory and the ability to easily view, edit, or delete specific memories.

### 5. Expanded Automation Engine
- **Accessibility Service Integration**: Fully implement and utilize the Android Accessibility Service for advanced UI automation, allowing the assistant to interact with any app on the device (e.g., filling forms, navigating complex menus).
- **Smart Home Integration**: Integrate with popular smart home platforms (e.g., Google Home, Amazon Alexa) to control smart devices via voice commands.
- **Customizable Routines**: Allow users to create custom routines or macros that combine multiple actions into a single command (e.g., "Good morning" routine that turns on lights, reads news, and starts coffee maker).
- **App-Specific Actions**: Develop specific integrations for frequently used apps (e.g., 
WhatsApp, Spotify) to perform actions like sending messages, playing music, or checking notifications.

### 6. UI/UX Improvements
- **Interactive Conversation UI**: Develop a more dynamic and visually appealing chat interface with animations, typing indicators, and rich content display (e.g., cards for information, interactive buttons for actions).
- **Customizable Themes**: While the current requirement is a light theme, offering customizable themes (including a dark mode) would enhance user experience.
- **Visual Feedback for Actions**: Provide clear visual feedback when an action is being executed (e.g., a small overlay showing "Calling..." or "Opening Spotify").
- **Onboarding and Tutorial**: Implement a guided onboarding process for new users, explaining the assistant's capabilities and how to use them effectively.

### 7. Performance and Reliability
- **Error Handling and Recovery**: Implement more sophisticated error handling mechanisms, especially for API calls and automation tasks, with graceful degradation and informative user feedback.
- **Background Process Monitoring**: Tools to monitor the background service's health and resource usage, with automatic restarts or notifications in case of issues.
- **Unit and Integration Testing**: Comprehensive test suite to ensure the reliability and stability of all components, especially the core AI and automation logic.

### 8. Security and Privacy
- **Secure API Key Management**: Implement best practices for storing and accessing API keys (e.g., using Android Keystore or environment variables during build time, rather than hardcoding).
- **Permission Management**: Provide clear explanations to users about why each permission is needed and allow them to easily manage permissions within the app.
- **Data Encryption**: Encrypt sensitive user data stored in the local Room Database.

By implementing these enhancements, the Android Assistant can evolve into a truly powerful, intelligent, and user-friendly personal AI companion.
