# 🛴 Swox 3D Garage

**Swox 3D Garage** is a high-performance, login-free, offline-first Android application built with **Kotlin** and **Jetpack Compose**. Designed as a comprehensive 3D inspection tool, it features an OLED-optimized dark interface, custom typography with full Azerbaijani/Turkish character support, and a massive universal catalog of rideables rendered in real-time 3D.

---

## ✨ Key Features

* **🔐 Login-Free & Offline-First Architecture:** Instant access upon launch with zero authentication barriers. Powered by **Room** for local persistence and **Firebase Firestore** for background catalog expansion.
* **🌐 Universal Rideables Catalog:** Supports thousands of rideable vehicles across multiple categories (E-Scooters, E-Bikes, Bicycles, Skateboards, and more) with up to 1,000 items per category, complete with pagination and lazy loading.
* **🧬 Text-Encoded 3D Rendering Pipeline:** Stores 3D model data as lightweight encoded text strings in the database and decodes them on background threads at runtime into interactive 3D meshes rendered via **SceneView / Filament**.
* **🎥 Smooth 3D Camera Controls:** Full 360° horizontal orbit (yaw), bounded vertical tilt (pitch between -60° and +60°), inertial damping, and pinch-to-zoom gestures.
* **🖤 Pure OLED UI & Custom Typography:** Crafted with a true `#060608` OLED black theme and **Orbitron** typography enhanced with a cohesive fallback chain for seamless rendering of localized characters (`ğ`, `ş`, `ç`, `ö`, `ü`, `ı`).

---

## 🛠️ Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **3D Graphics Engine:** SceneView / Google Filament
* **Local Database:** Room (SQLite) with Pagination
* **Cloud Backend:** Firebase Firestore (Public read / background sync)
* **Architecture:** MVVM + Repository Pattern + Kotlin Coroutines & Flow

---

## 🗄️ Firestore Data Structure

The cloud catalog is organized hierarchically by category to support scalable queries and pagination:

```text
[Category Name] (Collection: e.g., E-Scooters, E-Bikes, Bicycles)
 └── [Vehicle Model ID] (Document)
      ├── metadata / specs (Model details, speed, range, weight, etc.)
      └── encodedModel3D (Compressed/Base64 text-encoded 3D mesh string)
