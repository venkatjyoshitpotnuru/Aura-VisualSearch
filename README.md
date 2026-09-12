# A U R A ✨

Aura is a lightning-fast, offline, and privacy-first visual search application for Android that allows users to explore and find photos in their local gallery using natural language descriptions. Powered by on-device machine learning, everything runs locally on mobile hardware without sending personal images to any cloud server.

[![Download APK](https://img.shields.io/badge/Download-Aura_APK-4FA8FF?style=for-the-badge&logo=android)](https://github.com/venkatjyoshitpotnuru/Aura-VisualSearch/releases/latest/download/app-debug.apk)

## 🚀 Key Features

* **100% Offline AI Inference**: Utilizes **ONNX Runtime** to execute local machine learning models directly on mobile hardware.
* **CLIP ViT-B/32 Architecture**: Employs split vision and text encoders (`vision_encoder.onnx` and `text_encoder.onnx`) for cross-modal semantic matching.
* **Prompt Ensembling**: Automatically expands and averages multi-prompt variations for text queries to maximize zero-shot search accuracy.
* **Custom Cyber-Dark UI**: Designed with Jetpack Compose featuring a custom-tailored dark palette (Icy Blue, Lavender, Champagne Peach) and a high-resolution splash screen.
* **Adjustable Scan Depth**: Lets users scope their search limits dynamically (e.g., last 50, 100, 500, or all photos).

## 📥 Direct Installation
If you just want to test or use the app without compiling the source code:
1. Go to the [Releases Page](https://github.com/YOUR_USERNAME/YOUR_REPOSITORY/releases/latest) and download the latest `app-debug.apk`.
2. Transfer or open the APK file on your Android device.
3. Tap **Install** (allow installation from unknown sources if prompted).

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material 3)
* **ML Runtime**: ONNX Runtime for Android (`ai.onnxruntime`)
* **Concurrency**: Kotlin Coroutines & Flow

## 📂 Project Structure

```text
MySearchApp/
├── app/
│   ├── src/main/
│   │   ├── assets/              # Contains ONNX models and weights (.onnx, .data)
│   │   ├── java/com/example/mysearchapp/
│   │   │   ├── MainActivity.kt  # Main UI, search workflow, and state management
│   │   │   ├── OnnxEngine.kt    # On-device inference and similarity matching
│   │   │   └── ClipTokenizer.kt # Text tokenization for model inputs
│   │   └── res/                 # UI layouts, themes, and drawables
