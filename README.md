# NotiFlow

NotiFlow is an Android notification inbox focused on local-first notification history, rules-based prioritization, reminders, search, and privacy controls.

> Production status: **hardening in progress**. The current source is the source of truth; feature names and earlier phase-completion documents are not proof of runtime behavior.

## Current implementation truth

### Verified in source

- NotificationListenerService-based capture with excluded-package gating.
- Persistent SQLite notification history with update-by-notification-key behavior.
- Rules-first local classification; the core path does not require cloud AI.
- Pin, state, reminder, VIP, learned preference, search, digest, cleanup, and expense-domain code exists.
- Group-summary and ongoing/foreground-service notifications are filtered before persistence.
- Quiet-source/category cancellation is restricted to final, high-confidence low-priority promotion/spam/social classifications and protects VIP, pinned, OTP, and payment signals.
- OTP clipboard content is marked sensitive on supported Android versions, and OTP auto-copy is opt-in.
- Android app backup is disabled with `android:allowBackup="false"`.
- Gradle wrapper files are committed.

### Partial or intentionally constrained

- **Focus profiles:** manual and scheduled status are implemented, but focus-based notification suppression is intentionally not enforced until its policy is device-tested.
- **Local AI:** MediaPipe `tasks-genai:0.10.27` integration remains isolated behind `GemmaClassifier`. Automatic network model download is disabled. The current app has no production-ready verified model-import/acquisition flow, so rules-only mode is the safe default.
- **Restore:** the current v1 encrypted archive can decrypt and restore VIP rules, but does not restore notification history. UI does not claim a completed notification restore.
- **Anti-revoke:** NotiFlow preserves captured notification history after a source notification disappears; it does **not** claim to prove a sender used “Delete for everyone.”
- **Voice reader:** incoming notifications are wired to the TTS policy engine, but real routing/OEM/device behavior still requires physical-device verification.

## Privacy model

Notification content is stored locally in the app database. The current classifier has no cloud-inference fallback. Optional local AI is disabled unless the user explicitly enables it, and no automatic model network acquisition occurs.

Sensitive controls are opt-in where they create additional derived/exposed data:

- OTP auto-copy: off until explicit user consent.
- Expense parsing: off until explicit user consent.
- Local AI: off until explicit user consent.

Turning expense parsing off stops new derived financial rows; existing derived rows remain until the user deletes local history.

## Build baseline

Current build configuration:

- `compileSdk`: 37
- `targetSdk`: 37
- `minSdk`: 24
- Android Gradle Plugin: 9.4.0
- Kotlin Compose plugin: 2.4.10
- Gradle wrapper: 9.6.0
- Java: 17
- Compose BOM: 2026.09.00
- WorkManager: 2.12.0
- MediaPipe Tasks GenAI: 0.10.27
- app version: 0.1.0 (versionCode 1)

The wrapper is present: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle-wrapper.properties`.

## Build and test

From a machine with the Android SDK configured:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The repository also contains `.github/workflows/android.yml` on the production-hardening branch to run unit tests, lint, and a debug assembly. Until that workflow produces a successful run, CI remains **unverified**, not passed.

## Device verification still required

NotificationListenerService behavior, notification cancellation, clipboard presentation, TTS routing, WorkManager/OEM scheduling, reboot/rebind behavior, MediaPipe inference, and lock-screen behavior require physical-device coverage. At minimum test Android 13, 14, 15, 16, and the newest supported release across AOSP/Pixel plus major OEMs where available.

## Local AI runtime note

Google's current MediaPipe LLM Inference Android guide documents `com.google.mediapipe:tasks-genai:0.10.27` but now describes that API as maintenance-only and recommends LiteRT-LM for new work. Model format/identity must be verified against the exact runtime before enabling acquisition. NotiFlow therefore does not treat “non-empty model file” as sufficient production evidence and does not auto-download a model.

Official references:

- https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
- https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference
- https://developers.google.com/edge/litert-lm/android

## Audit

See `AUDIT_FINDINGS.md` on the production-hardening branch for the baseline, claim-vs-code table, feature truth table, prioritized defects, verification gates, and remaining device/Play work.
