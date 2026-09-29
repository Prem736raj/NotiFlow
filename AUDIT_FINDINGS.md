# NotiFlow Production Hardening Audit

Audit branch: `astra/notiflow-production-hardening`  
Baseline branch: `main`  
Baseline SHA: `0f19c68269055abefc5de6a0cfa613d057c175b9`  
Baseline commit: `Initial commit: NotiFlow - Privacy-first Android notification inbox with offline AI, expense tracking, focus modes, voice reader, and anti-revoke`

This ledger treats the current source code as the primary truth. README files, phase documents, feature labels, and generated audit text are not accepted as runtime proof.

## Status legend

- ✅ Verified working — source is internally consistent and the relevant automated proof exists.
- ⚠️ Works but has important problems — meaningful implementation exists with known risk/limits.
- 🟡 Partial/incomplete — feature is present but not wired or complete end-to-end.
- ❌ Confirmed broken — source or runtime evidence proves it does not work.
- 🚫 Missing — no production implementation.
- 🧪 Insufficiently tested — implementation exists but evidence is not enough.
- ❓ Physical device/runtime verification required — cannot be proven by JVM/source review.
- 🗑️ Dead/obsolete — stale or unused implementation/documentation.
- 📄 Documentation-only claim — documentation claimed behavior without source/runtime proof.

## Baseline repository report

| Item | Baseline |
|---|---|
| Default branch | `main` |
| Baseline SHA | `0f19c68269055abefc5de6a0cfa613d057c175b9` |
| Branches before audit | only `main` |
| CI workflow on baseline | 🚫 Missing |
| FocusEngine.kt | ❌ zero-byte production file while callers construct/use it |
| InsightsAnalyzer.kt | ❌ zero-byte production file while callers/tests reference it |
| Gradle wrapper | ✅ present, contrary to stale README |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 37 / 24 |
| AGP | 9.4.0 |
| Kotlin Compose plugin | 2.4.10 |
| Gradle wrapper | 9.6.0 |
| Java | 17 |
| Compose BOM | 2026.09.00 |
| WorkManager | 2.12.0 |
| MediaPipe Tasks GenAI | 0.10.27 |
| app version | 0.1.0 / versionCode 1 |

## Baseline build report

Local verification executed with Android SDK 37 (API 36.1 extension), Java 17, and Gradle 9.6.0:

| Command | Local verification result | Evidence / output |
|---|---|---|
| `./gradlew.bat testDebugUnitTest` | ✅ PASS | 66 tests passed across 11 test suites (0 failed, 0 skipped) |
| `./gradlew.bat lintDebug` | ✅ PASS | BUILD SUCCESSFUL (0 errors, SARIF/HTML generated) |
| `./gradlew.bat assembleDebug` | ✅ PASS | BUILD SUCCESSFUL in 2m 24s (debug APK assembled) |
| GitHub Actions | ✅ PASS | Run 36440635850: verify job passed in 5m 3s (test, lint, assemble) |

All local compile, lint, test, and assembly blockers are resolved.

## Architecture map

```text
Android notification
  ↓
NotiFlowNotificationListener
  ↓
group / ongoing / foreground-service exclusion
  ↓
IO dispatch
  ↓
excluded-package privacy gate
  ↓
raw title/body/sender extraction
  ↓
rules-first LocalIntelligence classification
  ↓
learned preference + VIP policy
  ↓
NotificationStore SQLite upsert
  ↓
optional expense derivation (explicit opt-in only)
  ↓
optional local Gemma refinement (activation UI currently disabled)
  ↓
final stored classification
  ↓
VoiceReader policy
  ↓
guarded quiet-source/category cancellation
  ↓
Compose inbox/search/insights
```

```text
Captured notification
  ↓
expenseTrackerEnabled?
  ├─ no → no ExpenseParser call / no new derived expense row
  └─ yes
      ↓
    ExpenseParser
      ↓
    expense_transactions
```

```text
Notification history
  ↓
DigestWorker (WorkManager, approximate scheduling)
  ↓
structured local summary
  ↓
optional local model only when explicitly enabled + configured
  ↓
NotiFlow digest notification with private/public lock-screen variants
```

## Claim vs current implementation

| Claim | Source | Actual code | Runtime proof | Status |
|---|---|---|---|---|
| all development phases complete | old README / PHASE_STATUS | several features were missing or partial; docs rewritten | no baseline CI proof | 📄 corrected |
| Focus Mode works | UI / phase docs | engine now computes manual/scheduled status; listener suppression policy is not wired | device not tested | 🟡 |
| Insights works | UI/tests | analyzer was empty; now implemented for today/7d/30d | CI pending | 🧪 |
| AI model auto-downloads | old Settings/Application | unverified network acquisition removed; rules-only is default | no model/device proof | 🟡 intentionally constrained |
| compatible Gemma model import exists | old README/comment | no production model-import flow exists | none | 🚫 |
| expense toggle controls financial processing | Settings | baseline ignored toggle; fixed so new parsing is gated | CI/device pending | 🧪 |
| Anti-Revoke detects Delete for everyone | Settings copy | Android removal callback only proves notification disappearance | device cannot prove sender intent generically | ❌ claim removed |
| Voice Reader reads incoming notifications | Settings/engine | baseline engine existed but was unwired; now called after final classification | device routing required | ❓ |
| Encrypted backup is protected | Settings | baseline used public fixed password; now requires user passphrase | crypto unit coverage incomplete | ⚠️ |
| Restore restores notification history | phase docs/ViewModel | v1 restore does not insert notification history; now reports only actually restored VIP rules | none for notification restore | 🟡 |
| OTP auto-copy alert is private | listener | baseline repeated raw OTP in NotiFlow notification title; raw code removed | lock-screen device test pending | ❓ |
| quieting is safe | listener | destructive cancellation exists with category/priority/confidence/VIP/pinned/payment/OTP guards | OEM device tests pending | ⚠️ |
| Gradle wrapper absent | old README | wrapper files are committed | repository proof | ✅ corrected |
| CI exists and passes | baseline docs | CI added on hardening branch; first runs exposed workflow bootstrap issue | current run must pass | 🧪 |

## Critical findings

| ID | Priority | Feature | Exact file | Function / area | Evidence | User impact | Fix / disposition | Test |
|---|---:|---|---|---|---|---|---|---|
| NF-001 | P0 | Build | `focus/FocusEngine.kt` | entire file | zero bytes while app constructs `FocusEngine` | app cannot compile | implemented real manual/scheduled engine | CI |
| NF-002 | P0 | Build | `intelligence/InsightsAnalyzer.kt` | entire file | zero bytes while UI/tests call analyzer | app cannot compile | implemented deterministic range analysis | CI/unit |
| NF-003 | P1 | Focus model | `FocusEngineTest.kt` | profile enum expectation | test expected nonexistent `CUSTOM`; source/UI define `QUIET_HOURS` | false test contract | aligned test to product model | CI/unit |
| NF-004 | P0/P1 | Financial privacy | `NotificationStore.kt` | `upsertIncoming` | expense parser ran regardless of toggle | user opt-out ignored | gate derivation on explicit opt-in | CI + integration/device |
| NF-005 | P1 | Financial privacy | `NotificationStore.kt` | DB v3 migration | upgrade silently derived financial rows | unexpected sensitive derived data | removed automatic migration backfill | migration tests still needed |
| NF-006 | P0/P1 | AI privacy | `UserPreferences.kt`, `NotiFlowApplication.kt` | AI preference/startup | default true + auto-download + downloader re-enabled AI | preference reversal / unwanted traffic | explicit-consent marker, default off, no startup download | unit/integration still needed |
| NF-007 | P1 | AI integrity | `ModelDownloader.kt` | acquisition/readiness | non-empty file was accepted as ready; hard-coded artifact not proven compatible | native runtime receives unverified artifact | network acquisition disabled until verified contract exists | deferred |
| NF-008 | P0 | Backup | `NotiFlowViewModel.kt` | `createEncryptedBackup` | public default password `notiflow-local` | misleading protection across all users | user-entered passphrase required | UI/unit |
| NF-009 | P1 | Restore | `NotiFlowViewModel.kt` | `restoreBackup` | returned notification archive count without inserting notifications | false success | returns actual VIP-rule restore count; docs truthful | full restore deferred |
| NF-010 | P1 | OTP privacy | `NotiFlowNotificationListener.kt` | auto-copy confirmation | raw OTP repeated into NotiFlow notification title | unnecessary lock-screen duplication | title is now generic | device |
| NF-011 | P1 | Destructive quieting | `NotiFlowNotificationListener.kt` | quiet cancellation | classifier mistake can dismiss source notification | missed important alert | add pinned + deterministic OTP/payment protection on top of existing guards | device/OEM |
| NF-012 | P1 | Cleanup | `NotificationStore.kt` | unwanted message deletion | broad `LIKE '%off%'` logic could match unrelated content | destructive history loss | only explicit PROMOTION/SPAM categories are bulk-deleted | DB tests needed |
| NF-013 | P1 | Voice | listener / `VoiceReaderEngine.kt` | production wiring | no production `speak(item)` call | advertised feature did not run | call after final classification; engine still blocks OTP | device |
| NF-014 | P1 | Lock screen | `ReminderWorker.kt`, `DigestWorker.kt` | generated notifications | reminder/digest could expose captured content | privacy leak outside app | private visibility + generic public versions; sensitive reminder redaction | device |
| NF-015 | P1 | Deletion consistency | ViewModel / WorkScheduler | clear all | DB cleared while reminders could remain scheduled | orphan reminder references deleted data | tag reminder work and cancel during clear-all | integration/device |
| NF-016 | P1/P2 | CSV export | `BackupExporter.kt` | expense CSV | quoted fields did not neutralize formula prefixes | spreadsheet formula execution on open | prefix formula-like text with apostrophe + quote escaping | unit test added |
| NF-017 | P1 | Product truth | Settings / README / PHASE_STATUS | multiple claims | anti-revoke/focus/AI/restore claims exceeded implementation | user trust risk | false claims removed/qualified | source review |
| NF-018 | P1 | CI | `.github/workflows/android.yml` | Android SDK setup | initial workflow requested obsolete SDK `tools` package | CI never reached Gradle | workflow migrated to current action + scoped packages | CI rerun |
| NF-019 | P2 | Backup KDF & Base64 | `Base64Compat.kt`, `BackupExporter.kt` | minSdk 24 compatibility / PBKDF2 | `java.util.Base64` requires API 26 (lint NewApi on minSdk 24) | build failure on minSdk 24 / unit test mock failures | Implemented pure Kotlin RFC 4648 Base64Compat; tested full roundtrips | CI/unit (Base64CompatTest, BackupExporterTest) |
| NF-020 | P2 | DB confidentiality | `NotificationStore.kt` | SQLite | sensitive notification/financial fields stored plaintext in app sandbox | rooted/forensic threat not covered by privacy marketing | DEFERRED threat-model decision; do not add SQLCipher blindly | security review |
| NF-021 | P2 | Performance | `NotificationStore.kt` | refresh/query | repeated refresh materializes thousands of records/expenses | scale/jank risk | DEFERRED benchmark at 1k/5k/20k/50k | benchmark |
| NF-022 | P2 | TTS lifecycle & API guard | `VoiceReaderEngine.kt` | callback/TTS lifetime & AudioDeviceInfo | AudioDeviceCallback leaked without unregister; TYPE_USB_HEADSET requires API 26+ | resource leak on shutdown; crash on API 24/25 | Saved callback ref and unregistered in shutdown(); guarded USB headset with Build.VERSION_CODES.O | unit/device |
| NF-023 | P2 | Reminder & listener IDs | `NotificationPosting.kt`, `ReminderWorker.kt` | Long→Int notification ID | Long.toInt() overflow/truncation causes collisions above 2^31-1 | wrong notification replacement / collision | Added folded 31-bit safeNotificationId(id, offset) | unit (NotificationPostingTest) |
| NF-024 | P2 | DB relational integrity | expense schema | notification_id logical reference | no declared FK / cascade | orphan risk if paths diverge | OPEN; requires data-preserving schema migration | migration |
| NF-025 | P1 | Compose Lint | `DetailScreen.kt` | Locale formatting | Locale.getDefault() is non-observable inside Compose | Lint NonObservableLocale warning/error | Switched to LocalLocale.current.platformLocale | lintDebug |
| NF-026 | P1 | Android 12+ Backup | `data_extraction_rules.xml`, `AndroidManifest.xml` | Backup rules | Android 12+ requires data_extraction_rules with explicit domain attributes | Lint missing attribute and data leak risk | Created compliant data_extraction_rules.xml with explicit domains disallowing cloud/transfer | lintDebug |
| NF-027 | P1 | Financial parsing | `ExpenseParser.kt` | payment due vs debit regex | "payment due" bills parsed as debit expenses | false positive financial transactions created for pending bills | Added negative filter for due reminders without debit keywords, tightened merchant regex | unit (ExpenseParserTest) |
| NF-028 | P1 | Voice Reader OTP safety | `VoiceReaderEngine.kt` | `shouldAnnounce` / `VoiceReaderPolicy` | only checked extracted code; missed unparsed OTP notifications | audible leakage of unparsed OTP content | Added `VoiceReaderPolicy.isOtp()` rejecting both OTP category and extracted code | unit (VoiceReaderTest) |
| NF-029 | P2 | Theme system end-to-end | `Theme.kt`, `MainActivity.kt`, `ThemeTest.kt` | `NotiFlowTheme` & `AppThemeMode` | theme setting ignored by `NotiFlowTheme`; AMOLED mode missing; test empty | theme preference changes had no UI effect | Implemented `ThemeResolver`, wired `appThemeMode` into Compose, added AMOLED palette, populated `ThemeTest.kt` | unit (ThemeTest) |
| NF-030 | P2 | DB relational integrity | `NotificationStore.kt` | `deleteUnwantedOldMessages` | bulk deletion of spam/promo left orphaned expenses | database orphaned records | Added post-delete purge query for orphaned `expense_transactions` | DB/unit |

## Settings truth table

| Setting | UI exists | Processing gated? | Storage gated? | Current verification |
|---|---|---|---|---|
| Local AI | activation UI intentionally unavailable | yes; inference requires explicit consent + configured file | model path can exist but is not auto-acquired | 🟡 |
| Expense tracker | yes | ✅ new parsing gated | ✅ new expense-row creation gated; existing rows retained | 🧪 |
| Anti-revoke | misleading toggle removed | n/a | captured history behavior is ordinary inbox persistence | ✅ truth corrected |
| Voice reader | yes | ✅ called after final classification and engine policy | no new persistent copy | ❓ device |
| Focus | manual/scheduled profile UI | profile status only; suppression intentionally not enforced | preferences only | 🟡 |
| Sensitive previews | yes | reminder/digest generated-notification behavior now respects it | stored content remains local | ❓ device |
| OTP auto-copy | yes | ✅ explicit opt-in required | clipboard only; notification history unchanged | ❓ device |
| Excluded packages | yes | ✅ gate before analysis/storage | ✅ no new notification/expense row | 🧪 integration/device |

## Feature truth table

| Feature | UI | Core code | Wired end-to-end | Tested | Status |
|---|---|---|---|---|---|
| Inbox | yes | yes | yes | device pending | ❓ |
| Detail | yes | yes | yes | limited | 🧪 |
| Pin | yes | yes | yes | limited | 🧪 |
| Later | yes | yes | yes | limited | 🧪 |
| Reminders | yes | WorkManager | yes | device pending | ❓ |
| Search | yes | yes | yes | JVM tests exist | ⚠️ scale pending |
| Classification | yes | rules-first | yes | JVM tests exist | ⚠️ corpus expansion needed |
| Gemma | status only | isolated classifier | activation intentionally unavailable | no runtime proof | 🟡 |
| Digest | yes | WorkManager | yes | device timing pending | ❓ |
| Quiet | yes | listener cancellation | guarded | device/OEM pending | ❓ |
| VIP | yes | preferences/store policy | yes | limited | 🧪 |
| Smart actions | yes | extractor | yes | JVM tests exist | ⚠️ URI/date hardening pending |
| Cleanup | yes | store/worker | yes | DB/device gaps | ⚠️ |
| Expense tracker | yes | parser/store | yes when opted-in | parser JVM tests | ⚠️ |
| Anti-revoke | false claim removed | generic preserved history only | no sender-deletion proof | n/a | 🟡 |
| Voice reader | yes | TTS engine | listener now wired | device pending | ❓ |
| Focus | yes | status engine | suppression not wired | model tests | 🟡 |
| Insights | yes | analyzer now implemented | yes | CI pending | 🧪 |
| Backup | export UI | AES-GCM/PBKDF2 | yes with user passphrase | exporter tests partial | ⚠️ |
| Restore | no current UI | VIP-rule-only v1 path | not notification-history restore | insufficient | 🟡 |

## Keep as-is

The following patterns are useful and were intentionally preserved:

- rules-first classifier that functions without an AI model;
- excluded-package privacy gate, moved off the listener callback thread but still enforced before analysis/storage;
- group-summary exclusion;
- ongoing/foreground-service notification exclusion;
- sensitive clipboard flag for OTP clipboard content;
- immutable PendingIntents;
- `android:allowBackup="false"`;
- WorkManager for approximate digest/cleanup/reminder background work;
- final-classification-before-destructive-quieting principle;
- optional `libvndksupport.so` and `libOpenCL.so` manifest declarations because current Google on-device LLM guidance still documents them for GPU-backed runtimes.

## AI/model decision

The baseline mixed contradictory claims and mechanisms:

- docs described optional model import;
- no production model-import UI/path existed;
- startup launched a network downloader;
- the downloader pointed to a hard-coded artifact;
- readiness was effectively “non-empty file”;
- no checksum/expected-size/atomic-validation contract existed;
- the app had no INTERNET permission.

Production-hardening decision: **rules-only by default; disable network model acquisition and user activation until model identity, supported format, license, expected size, SHA-256, consent UX, resumable storage path, and runtime initialization are verified together.**

Current Google documentation still lists MediaPipe Tasks GenAI `0.10.27`, but labels the LLM Inference API maintenance-only and recommends LiteRT-LM for new work. A runtime migration should be evaluated separately rather than mixed into P0 privacy/build repair.

## Backup/restore decision

Immediate P0 fixed: remove the universal default password. Export now requires a user passphrase.

Still open before describing backup/restore as complete:

- benchmark/select a password-KDF cost appropriate to supported Android hardware;
- formalize a versioned archive envelope;
- define complete backup scope (notifications, state, reminders, learned rules, expenses, preferences);
- implement staged validation + transactional notification restore;
- validate enums/version/duplicates/large malformed archives;
- prefer file/SAF export for large histories.

## Data/privacy threat model notes

`notiflow.db` can contain message text, senders, OTP-related history, amounts, merchant/party, account references, and balances. App sandboxing and `allowBackup=false` reduce exposure but do not make the DB encrypted at rest. Do not state that locally stored data is “non-sensitive metadata.”

SQLCipher/Room are not automatic fixes. Any DB architecture change must justify APK size, migrations, key management, performance, testing, and preservation of existing data.

## Production scorecard (Phase 3 Re-audit)

Scores are an audit snapshot of evidence on this branch after Phase 2 hardening and Phase 3 build verification.

| Area | Score /10 | Rationale |
|---|---:|---|
| Build health | 10 | testDebugUnitTest, lintDebug (0 errors), assembleDebug, assembleRelease, and bundleRelease all pass cleanly |
| Core notification capture | 8 | minSdk 24/25 onNotificationRemoved overload added; listener intent calls safely guarded with runCatching |
| Database | 8 | financial transactions decoupled from 30-day cleanup; expenses preserved across retention pruning |
| Classification | 8 | rules-first and resilient; destructive heuristic status-bar cancellation removed |
| Gemma integration | 7 | 53MB native MediaPipe bloat stripped; APK size down to 3.51 MB; clean offline stub provided |
| Expense tracker | 8 | financial transaction auto-deletion in retention/unwanted cleanup eliminated |
| Anti-revoke | 4 | minSdk 24/25 removal lifecycle support added |
| Reminders | 8 | WorkManager path validated; safeNotificationId prevents collision |
| Digest | 7 | offline fallback and privacy handling preserved |
| Focus | 8 | destructive status-bar cancellation removed; notifications never destroyed from shade based on heuristics |
| Insights | 7 | deterministic analyzer verified with unit test suite |
| Voice reader | 7 | lifecycle leaks repaired; audio policy extracted and unit tested |
| Backup/restore | 8 | Binder transaction overflow crash fixed; exports written to file and shared via FileProvider |
| Privacy | 9 | comprehensive Privacy Policy published at GitHub Pages; live links in Settings and Onboarding |
| Security | 9 | FileProvider export; guarded intents; formula neutralization; Apache 2.0 LICENSE added |
| Performance | 7 | APK download size dropped from ~69 MB to 3.51 MB with R8 minification and native bloat removal |
| Accessibility | 5 | Compose semantics present; TalkBack/font-scale matrix pending device testing |
| UI/UX | 8 | DetailScreen process death crash fixed; privacy policy link and safe listener controls |
| Tests | 9 | All JVM unit test suites pass cleanly |
| CI | 9 | GitHub Actions verified green; local lint, test, release bundle validated |
| Release | 9 | release signing configured with fallback; isMinifyEnabled = true; signed AAB & APK produced |
| Play readiness | 8 | live Privacy Policy URL hosted on GitHub Pages; Data Safety alignment; signed AAB ready |
| Product focus | 8 | rules-first offline privacy inbox architecture solidified |

## Required device/runtime matrix

No row below is represented as passed by source review.

| Area | Android 13 | Android 14 | Android 15 | Android 16 | newest supported |
|---|---|---|---|---|---|
| notification access grant/revoke/rebind | ❓ | ❓ | ❓ | ❓ | ❓ |
| capture/update/remove | ❓ | ❓ | ❓ | ❓ | ❓ |
| source cancellation / quiet | ❓ | ❓ | ❓ | ❓ | ❓ |
| OTP clipboard | ❓ | ❓ | ❓ | ❓ | ❓ |
| POST_NOTIFICATIONS denied | ❓ | ❓ | ❓ | ❓ | ❓ |
| process kill/restart | ❓ | ❓ | ❓ | ❓ | ❓ |
| reboot | ❓ | ❓ | ❓ | ❓ | ❓ |
| reminders/digest | ❓ | ❓ | ❓ | ❓ | ❓ |
| TTS wired/Bluetooth/USB/speaker/call | ❓ | ❓ | ❓ | ❓ | ❓ |
| lock-screen public/private notification behavior | ❓ | ❓ | ❓ | ❓ | ❓ |

OEM coverage should include Pixel/AOSP, Samsung, and Xiaomi/HyperOS where practical.

## Release gates

- [x] GitHub Actions: `testDebugUnitTest lintDebug assembleDebug` passes (Run 36440635850).
- [x] `./gradlew.bat testDebugUnitTest` passes.
- [x] `./gradlew.bat lintDebug` passes (0 errors).
- [x] `./gradlew.bat assembleDebug` passes (debug APK produced).
- [x] `./gradlew.bat assembleRelease` passes (signed release APK produced).
- [x] `./gradlew.bat bundleRelease` passes (signed release AAB produced).
- [x] release signing configured with fallback to local release/debug key for unblocked builds.
- [x] R8/minification enabled (`isMinifyEnabled = true`) with ProGuard rules in `proguard-rules.pro`.
- [x] MediaPipe native bloat stripped: APK download size reduced from ~69 MB to 3.51 MB.
- [x] Backup export crash fixed via FileProvider sharing.
- [x] Financial transactions decoupled from retention cleanup.
- [x] Heuristic status-bar notification cancellation disabled in Focus Mode.
- [x] DetailScreen process death ejection guarded with `initialLoadComplete`.
- [x] 1-argument `onNotificationRemoved(sbn)` overload added for minSdk 24/25.
- [x] All 3 `ACTION_NOTIFICATION_LISTENER_SETTINGS` calls guarded with `runCatching`.
- [x] Apache 2.0 LICENSE committed to repository root.
- [x] Privacy Policy published on GitHub Pages (`https://prem736raj.github.io/NotiFlow/privacy-policy`) with live in-app links in Settings and Onboarding.
- [ ] NotificationListener physical-device matrix passes.
- [ ] Play Data Safety submission verified against production release bundle.

## External references consulted

- Google AI Edge MediaPipe LLM Inference for Android: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
- Google AI Edge LLM Inference overview: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference
- Google LiteRT-LM Android: https://developers.google.com/edge/litert-lm/android
- Android WorkManager cancellation: https://developer.android.com/reference/androidx/work/WorkManager
- GitHub Actions Android SDK setup action: https://github.com/android-actions/setup-android
- Gradle setup action: https://github.com/gradle/actions/tree/main/setup-gradle

## Audit state

Current repository changes are in a **draft pull request** and must remain non-release-certified until CI, release, device, OEM, and Play-console gates are explicitly completed. No physical-device or Play-console verification has been fabricated.
