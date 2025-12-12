# 🔐 Gesture AppLock — Android Kotlin App

Gesture AppLock is a modern Android application built in **Kotlin**, allowing users to lock any installed app using **custom gestures**.  
The user draws 2–3 samples of a gesture, and the app uses smart matching to verify it when unlocking.

This project includes:
- Foreground app monitoring  
- Gesture creation & recognition  
- Lock screen overlay  
- Modern UI with search  
- Supports both system preinstalled apps and downloaded apps  

---

## 🚀 Features

### 🔹 App Locking With Gestures
- Lock any app installed on the device
- Create **multiple gesture samples** for improved accuracy
- Unlock by drawing the saved gesture
- Secure and fast matching algorithm

### 🔹 Smart Unlock Logic
- Unlock screen appears **only when needed**
- Once unlocked, it stays unlocked until:
  - App is fully closed or removed from recents  
  - Another locked app is opened

### 🔹 Beautiful and Responsive UI
- Modern interface
- RecyclerView with app icons & names
- Search bar to quickly find apps

### 🔹 Complete Control
- Toggle lock on/off
- Add/remove gestures anytime
- Supports Android 10–14+

---


## 🛠️ Tech Stack

| Technology | Purpose |
|-----------|---------|
| **Kotlin** | Main language |
| **AndroidX** | UI & lifecycle|
| **GestureOverlayView** | Capturing gestures |
| **GestureLibrary (.gst)** | Saving & recognizing gestures |
| **UsageStatsManager** | Detecting foreground app |
| **Foreground Service** | Monitoring apps in background |
| **ViewBinding** | Clean view access |
| **RecyclerView** | App listing |
| **Material Components** | Modern UI |

---

## 📂 Project Structure

app/
└── java/com.my8a.gestureapplock/
├── ui/
│ ├── MainActivity.kt
│ ├── SetGestureActivity.kt
│ └── UnlockActivity.kt
│
├── data/
│ ├── AppUtils.kt
│ ├── GestureStore.kt
│ └── InstalledApp.kt
│
└── service/
└── ForegroundAppMonitorService.kt

---

## 📲 Installation & Setup

### 1️⃣ Clone the Repository

git clone https://github.com/hafizfahad1175/GestureAppLock.git

### 2️⃣ Open in Android Studio

File → Open

Select the root folder

Let Gradle sync

### 3️⃣ Set Required Permissions
The app needs:

Permission	Purpose
PACKAGE_USAGE_STATS	Detect foreground app
SYSTEM_ALERT_WINDOW	Show unlock screen
POST_NOTIFICATIONS	Foreground service notification

These are requested in‑app using settings screens.

🧩 How It Works
🔍 Detect Foreground App
The service uses:

UsageStatsManager (Android 10+)

Scans the last used event

Detects transitions between apps

## ✏ Create Gesture
User draws 3 gesture samples:

Stored in gesturelib.gst

Mapped to the specific package

Later matched with a similarity threshold

## 🔓 Unlock Flow
User opens a locked app

UnlockActivity appears

User draws gesture

Match → App unlocked

Added to UnlockedCache until app closed

## 🎨 UI Highlights
Material Design App Cards

Crisp app icon display

Search bar at top

Clean gesture drawing screen

Light & Dark theme support

## 🤝 Contributing
Pull requests are welcome!
You can improve:

UI polish

Better gesture matching

Background performance

Settings & themes

## 🐛 Known Issues / Future Improvements
Optimize gesture comparison speed

Add biometric unlock fallback

Backup/restore gesture library

## 📜 License
This project is open-source.
You may use or modify it for personal or academic purposes.

⭐ If You Like This Project…
Star the repository on GitHub to support further development!
