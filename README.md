# AutoDroid

<div align="center">

**Native Android Client for Open-AutoGLM**

Run AI Assistants directly on your phone, no PC required.

[English](README.md) | [中文](README_CN.md)

> This is an independent Android implementation and is not an official Open-AutoGLM client.

</div>

## Introduction

AutoDroid is a native Android implementation of the [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) project. Unlike the original project which controls the phone via ADB from a PC, AutoDroid is a complete Android application that runs independently on your device.

**Key Features:**
- 🤖 **Native Android App** - Runs directly on the phone, no PC needed.
- 🎤 **Voice Control** - Supports voice commands for tasks.
- 📱 **Automated Actions** - Controls apps via Accessibility Service.
- 🔒 **Secure Storage** - API configurations are encrypted.
- 🌐 **Custom API** - Supports self-hosted or third-party AI services.
- ✨ **Direct Injection** - Implements text input via native Accessibility Service injection, no keyboard switching required.

## Demo

<div align="center">
  <img src="assets/screenshots/home.png" width="30%" alt="Home"/>
  <img src="assets/screenshots/history.png" width="30%" alt="History"/>
  <img src="assets/screenshots/settings.png" width="30%" alt="Settings"/>
</div>

Simply:
1. Enable Accessibility Service.
2. Configure your AI Model API address.
3. Input a task via voice or text (e.g., "Open TikTok and search for dance videos").
4. The AI completes the entire operation automatically.

## Tech Stack

### Architecture
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Repository Pattern
- **DI**: Hilt (Dagger)
- **Network**: Retrofit + OkHttp
- **Storage**: EncryptedSharedPreferences
- **Voice**: Sherpa-ONNX (Offline High-Accuracy Recognition)
    - Model: Paraformer 2024-03-09
    - Accuracy > 90%
- **Kotlin**: 1.9.x
- **Min SDK**: 26 (Android 7.0+)
- **Target SDK**: 34

### Core Components

#### 1. AutoAgentService (Accessibility Service)
Location: `app/src/main/java/com/autoglm/autoagent/service/AutoAgentService.kt`

The core service for automation:
- Screen capture and UI tree analysis.
- Gestures: Click, Long Press, Scroll.
- Text input via direct Accessibility API injection.

```kotlin
// Main Functions
- click(x, y)          // Click
- longPress(x, y)      // Long Press
- scroll(x1, y1, x2, y2) // Scroll
- takeScreenshotAsync() // Screenshot
```

#### 2. AIClient (AI Communication)
Location: `app/src/main/java/com/autoglm/autoagent/data/api/AIClient.kt`

Communicates with AI Model Services:
- Compatible with OpenAI Chat Completions API format.
- Supports Multimodal Input (Text + Image).
- Configurable Base URL, API Key, Model.

```kotlin
// API Parameters
temperature: 0.0        // Strict Mode
top_p: 0.85
frequency_penalty: 0.2
max_tokens: 3000
```

#### 3. AgentRepository (Business Logic)
Location: `app/src/main/java/com/autoglm/autoagent/data/AgentRepository.kt`

Coordinates task execution flow:
- Manages conversation history.
- Execution Loop: Screenshot → AI Inference → Action Execution.
- State and Log Management.

## Setup Guide

### 1. Installation
Install AutoDroid.apk (approx. 100 MB).

### 2. Permissions
1. **Accessibility Service**
   - Open AutoDroid, click "Enable Accessibility Service".
   - Find and enable "AutoAgent Service" in system settings.
2. **Other Permissions**
   - Microphone (Voice Input)
   - Network (AI Communication)

### 3. API Configuration
Go to Settings and enter your model API information.

**Example (Zhipu AI):**
- Base URL: `https://open.bigmodel.cn/api/paas/v4`
- Model: `GLM-4.6V-Flash`
- API Key: `Your API Key`

## Comparisons

| Feature | Open-AutoGLM (Original) | AutoDroid (This Project) |
|---|---|---|
| **Runtime** | PC controls Phone via ADB | Runs directly on Phone |
| **Input** | CLI or Python API | Voice / Text UI |
| **Complexity** | Requires Python & ADB | Install APK only |
| **Screenshot** | ADB screencap command | Accessibility Service API |
| **Execution** | ADB input command | Accessibility Gestures + Native Text Injection |
| **Use Case** | Dev/Test, Batch Tasks | Daily Personal Use |

## Limitations

1. **Accessibility Limits**
   - Some system or secure apps may restrict accessibility access.
2. **Screenshot Limits**
   - Banking/Payment apps may block screenshots.
3. **APK Size**
   - Approx. 100 MB (includes offline voice models).
4. **OS Version**
   - Requires Android 7.0+.

## Roadmap

- [x] ~~Online Voice Recognition~~ - Upgraded to Sherpa-ONNX (Offline)
- [ ] Task History
- [ ] Custom Shortcuts
- [ ] UI Polish & Animations
- [ ] Multi-language Support
- [ ] Performance & Stability Improvements

## Third-party Components and Referenced Projects

This project uses the following open-source components or references:

- **Open-AutoGLM** (Apache License 2.0) - Automation agent design and protocol reference
- **Sherpa-ONNX** (Apache 2.0) - Offline Speech Recognition Engine
- **Paraformer Model** (Apache 2.0) - Chinese Speech Recognition Model
- **AutoGLM-Phone-9B family** (See original model license) - Large language model used via API

See [Third Party Licenses](./THIRD_PARTY_LICENSES.md) for details.

## Disclaimer

⚠️ **Disclaimer**: This project is for research and learning purposes only. It is strictly prohibited for any illegal use.

---

**Developer**: Aell Xin
**Last Updated**: 2026-01-03
