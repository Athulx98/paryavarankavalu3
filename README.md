# Paryavaran Kavalu 🌱

**Paryavaran Kavalu** (Malayalam/Kannada for *"Environment Guardian"*) is an Android app that helps people report, track, and clean up environmental issues — especially waste and littering — in their community.

## ✨ Features

- **📸 Report issues** — Capture a photo of waste or environmental hazards and submit a report with your location.
- **🤖 AI waste classification** — Uses an on-device TensorFlow Lite model (with Google ML Kit and Gemini AI support) to automatically classify waste types from images, even offline.
- **✅ Cleanup verification** — Verify that a reported issue has actually been cleaned up using before/after image comparison.
- **🗺️ Map view** — See reported issues plotted on a Google Map based on their location.
- **👥 Community** — Browse reports and cleanup activity shared by other users.
- **🔔 Notifications** — Get push notifications (via Firebase Cloud Messaging) for updates on reports and community activity.
- **👤 Authentication & profiles** — Sign in and manage your profile via Firebase Auth.

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (ViewModel + Repository pattern)
- **Local storage:** Room database
- **Backend:** Firebase (Auth, Firestore, Storage, Cloud Messaging)
- **Maps & Location:** Google Maps Compose, Play Services Location
- **Camera:** CameraX
- **AI / ML:** TensorFlow Lite (offline waste classifier), Google ML Kit (image labeling & object detection), Gemini generative AI
- **Image loading:** Coil

## 📋 Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK 35 (minSdk 26, targetSdk 34)

## 🚀 Getting Started

1. **Clone the repo**
   ```bash
   git clone https://github.com/Athulx98/paryavarankavalu3.git
   cd paryavarankavalu3
   ```

2. **Add your API keys**

   Create a `local.properties` file in the project root (if it doesn't already exist) and add:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key
   MAPS_API_KEY=your_google_maps_api_key
   ```

3. **Add Firebase config**

   Place your `google-services.json` file (from your Firebase project) in the `app/` directory.

4. **Build & run**

   Open the project in Android Studio and run it on an emulator or physical device, or from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

## 📁 Project Structure

```
app/src/main/java/com/paryavarankavalu/paryavarankavalu/
├── ai/            # Waste classification (TFLite) & cleanup verification
├── model/         # Data models
├── repository/     # Room database & app repository
├── service/       # Firebase messaging, location & AI services
├── ui/            # Theme and shared UI components
├── uii/screen/    # App screens (Home, Map, Community, Reports, Profile, Auth, etc.)
└── viewmodel/     # ViewModels
```

## 🤝 Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.

## 📄 License

_Add your chosen license here (e.g. MIT, Apache 2.0)._
