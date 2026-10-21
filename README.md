# NotiFlow

NotiFlow is an Android notification inbox focused on local-first notification history, rules-based prioritization, reminders, search, and privacy controls.

> Production status: **hardening in progress**. The current source is the source of truth; feature names and earlier phase-completion documents are not proof of runtime behavior.

## Current implementation truth

### Verified in source

- NotificationListenerService-based capture with excluded-package gating.
- Persistent SQLite notification history with update-by-notification-key behavior.
- Rules-first local classification; the core path does not require cloud AI.
- Pin, state, reminder, VIP, learned preference, search, digest, cleanup, and expense-domain code exists.
- The privacy policy is embedded in the app and works offline; no external URL is required.
- Group-summary and ongoing/foreground-service notifications are filtered before persistence.
- Quiet-source/category cancellation is restricted to final, high-confidence low-priority promotion/spam/social classifications and protects VIP, pinned, OTP, and payment signals.
- OTP clipboard content is marked sensitive on supported Android versions, and OTP auto-copy is opt-in.
- Android app backup is disabled with `android:allowBackup="false"`.
- Optional app lock delegates to the device screen lock; NotiFlow stores no separate PIN or password.
- Encrypted archive export can be restored into local notification history and VIP rules with duplicate-key protection; reminder schedules are not recreated.
- Gradle wrapper files are committed.

### Partial or intentionally constrained

- **Encrypted archive export:** v3 archives are password-protected and include notification fields plus VIP rules. Restore is available in Settings and skips duplicate notification keys; derived expense rows are not included in the archive.
- **Removed surface area:** the non-functional local AI/model path, Focus Mode, Smart Presets, voice reader, unwanted-cleanup prompt, and anti-revoke toggle have been deleted from the app rather than left as misleading toggles.

## Privacy model

Notification content is stored locally in the app database. Classification uses deterministic on-device rules; there is no cloud inference fallback and no model download path. The privacy policy is available offline inside the app.

Sensitive controls are opt-in where they create additional derived/exposed data:

- OTP auto-copy: off until explicit user consent.
- Expense parsing: off until explicit user consent.
- Automatic cleanup and scheduled digest: off by default.
- Sensitive previews: hidden by default.

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
- Bundled generative-model runtime: none (local rules-first fallback is active)
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

The repository also contains `.github/workflows/android.yml`, which requests unit tests, lint, a debug build, a release bundle, and instrumented Android tests. Release packaging fails unless `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` are explicitly configured; debug builds do not require them.

## Device verification still required

NotificationListenerService behavior, notification cancellation, clipboard presentation, WorkManager/OEM scheduling, reboot/rebind behavior, and lock-screen behavior require physical-device coverage. At minimum test Android 13, 14, 15, 16, and the newest supported release across AOSP/Pixel plus major OEMs where available.

## Audit

See `AUDIT_FINDINGS.md` for the engineering audit, [the product and growth roadmap](docs/NOTIFLOW_GROWTH_ROADMAP_2026.md) for priorities and metrics, and [release preparation](docs/RELEASE_PREPARATION.md) for safe signing and GitHub Pages deployment instructions.
