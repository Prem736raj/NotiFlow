# NotiFlow

NotiFlow is a privacy-first Android notification inbox that helps users separate what matters now from what can wait.

**Product line:** Important now. Deal with some later. Digest the noise.

## What is implemented

All 16 planned phases are represented in the app:

1. Premium onboarding that works even when access is declined.
2. Persistent notification inbox using Android notification-listener access.
3. Notification detail, important state, pinning, later/done/archive/delete, and source-app return when available.
4. Keyword search plus app/category/state/priority filters.
5. Rules-first on-device classification with optional local Gemma refinement.
6. Smart priority scoring with OTP priority decay.
7. User corrections and learned per-source/per-sender preferences.
8. Scheduled daily digest with structured local fallback and optional local-model summary.
9. Quiet sources and quiet categories, while protecting important/VIP items.
10. Notification reminders backed by WorkManager.
11. VIP senders and VIP apps.
12. Smart extraction for OTP codes, links, phone numbers, addresses, dates/calendar events, and reminders.
13. Natural-language notification search such as "Amazon delivery from last week" or "payment around 2000 rupees".
14. Automatic cleanup with faster expiry for OTPs/promotions/spam and retention controls.
15. Privacy controls, local data deletion, learned-rule deletion, AI disable/remove-model controls, and sensitive-preview masking.
16. Release-oriented states for missing permissions, missing models, offline use, duplicates, device restart persistence, large history, light/dark appearance, and accessibility-friendly Compose layouts.

## Privacy model

- Notification history is stored locally in the app database.
- Android cloud backup is disabled for the app.
- Core classification works without a cloud service or paid API.
- No notification text is sent to an external AI service by this codebase.
- The optional Gemma model is stored in app-private storage after the user imports it.
- Removing the model does not break the rules-first classifier.

## Optional local AI

The app is useful without any model. When the user imports a compatible Gemma `.task` model, ambiguous notifications can be refined and digests can use local text generation.

The project currently uses the known MediaPipe Tasks GenAI Android API behind one isolated `GemmaClassifier` file. This keeps the rest of the app independent from the model runtime and makes a later runtime migration straightforward.

A Gemma 3 270M mobile `.task` model is roughly hundreds of MB, so it is intentionally **not bundled in the APK**. This avoids forcing every user to download a large model before they can use the app.

## Android/build baseline

- `compileSdk`: 37
- `targetSdk`: 37
- `minSdk`: 24
- Android Gradle Plugin: 9.4.0
- Compose BOM: 2026.09.00
- Java: 17
- WorkManager: 2.12.0

This folder was generated while the live Android Studio tunnel was unavailable. It intentionally does not include a Gradle wrapper binary. The original plan assumes an existing Hello World Compose template; use that template's wrapper, or configure Gradle 9.6 in Android Studio for AGP 9.4.

## First device run

1. Open/sync the project in Android Studio.
2. Install on a physical Android phone.
3. Launch NotiFlow and grant notification-listener access.
4. Allow NotiFlow notifications if you want reminders/digests.
5. Receive a few notifications and verify the inbox/categories.
6. Optionally import a compatible Gemma `.task` model from Settings → On-device intelligence.

The optional LLM runtime is best validated on a physical device rather than an emulator.

## Validation already performed in this session

- Pure Kotlin intelligence files compile with `kotlinc`.
- Logic smoke checks pass for OTP, payment, promotion, amount extraction, and natural-language search parsing.
- All XML resources parse successfully.
- A parser pass over all Kotlin files found no Kotlin syntax errors.

A full Android Gradle compile could not be run in this environment because the Android SDK is not installed and the live Android Studio workspace tunnel was disconnected.

## Important Android behavior

NotiFlow can cancel selected low-priority notifications after the listener observes them, but Android/OEM behavior can vary and the source notification may briefly appear first. The UI communicates this limitation instead of claiming perfect blocking.

WorkManager is used for battery-conscious scheduled work. Daily digest delivery may occur slightly after the selected clock time under Android background scheduling rules.
