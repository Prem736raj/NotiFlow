# Development phase status

This file is a status index, not proof. Runtime/build proof must come from CI, tests, and physical-device verification.

| Phase | Status | Current source truth |
|---|---|---|
| 1 | 🟡 Partial | onboarding/access UI exists; device permission/revoke/rebind behavior still needs device verification |
| 2 | 🟡 Partial | listener, SQLite history, duplicate-key upsert exist; OEM/runtime verification remains |
| 3 | ✅ Source-verified | detail/state/pin/VIP/later/delete exist; encrypted archive export and notification-history/VIP/automation-rule restore are wired |
| 4 | ✅ Source-verified | keyword + structured filters implemented; scale benchmarking still pending |
| 5 | ✅ Source-verified | rules-first classifier only; inactive local-model path was removed rather than shipped as a stub |
| 6 | ✅ Source-verified | priority scoring and 15-min OTP decay verified by unit tests; broader real-world corpus pending |
| 7 | ✅ Source-verified | learned per-source/per-sender corrections and removal controls exist |
| 8 | 🟡 Partial | WorkManager digest exists; exact-clock delivery is not promised and device scheduling remains unverified |
| 9 | ⚠️ Guarded | quiet cancellation exists with VIP/pinned/sensitive/category/confidence guards; OEM/device tests required |
| 10 | 🟡 Partial | WorkManager reminders exist and clear-all now cancels reminder work; device tests required |
| 11 | ✅ Source-verified | VIP sender/app rules exist and current quiet cancellation protects VIP items |
| 12 | 🧪 Insufficiently tested | smart-action extraction exists; malicious URI/date/locale coverage remains |
| 13 | 🟡 Partial | rule-based natural-language interpretation exists; do not describe it as semantic vector search |
| 14 | ⚠️ Guarded | retention/cleanup exists; destructive broad “%off%” deletion was removed |
| 15 | 🟡 Partial | in-app offline policy, safe defaults, archive restore, and device-credential app lock exist; DB-at-rest threat model remains |
| 16 | ✅ CI passed | GitHub Actions run 39756707962 passed verify (unit tests, lint, debug/release builds, non-debug certificate check) and instrumented jobs on 22 October 2026; local suite has 82 tests across 13 suites |
| 17 | Source-verified | keyword automation rules for pin, high priority, and auto-archive are applied during capture; storage/matching tests pass |
| 18 | Source-verified | multi-select Done, one-hour snooze, Archive, and Delete are wired through bulk store operations |
| 19 | Insufficiently tested | aggregate-count home-screen widget compiles and updates from store changes; launcher behavior needs device verification |
| 20 | Source-verified | optional off-by-default WhatsApp media saving: scoped permissions, MediaStore observer + hourly work, app-private copies with SHA-256 dedupe, saved-media viewer, per-item and bulk delete; view-once media is impossible to save and excluded from export archives; runtime behavior needs device verification |

## Status legend

- ✅ Source-verified: implementation is present and internally consistent in source; may still have separate device gates.
- ⚠️ Guarded: works in source with important safety constraints or known limitations.
- 🟡 Partial: meaningful implementation exists but is not end-to-end complete.
- ❌ Broken: confirmed nonfunctional.
- 🚫 Missing: no implementation.
- 🧪 Insufficiently tested: implementation exists but proof is not adequate.
- ❓ Device verification required: cannot be proven from JVM/source review alone.
