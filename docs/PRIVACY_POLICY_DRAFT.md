# NotiFlow Privacy Policy — Release Draft

**Status:** release-blocking draft. Before publication, add the legal developer/publisher identity, a monitored privacy contact mechanism, effective date, public non-editable URL, and final APK/SDK transmission verification.

NotiFlow is a notification-management app that can read notifications after the user grants Android Notification Access. This privacy policy explains what information the app accesses, how it is used, where it is stored, and what users can control.

## Data NotiFlow can access

If the user grants Notification Access, NotiFlow can receive notification fields from enabled apps. Depending on the source app, this can include:

- source application/package;
- notification title and message/body text;
- sender or conversation information exposed by Android;
- notification timestamps and status;
- verification codes contained in notification text;
- banking/payment/transaction information contained in notification text, such as amount, merchant or party, account reference, and balance text.

Notification content can contain personal and sensitive information. NotiFlow does not describe this data as anonymous or non-sensitive metadata.

## How notification data is used

NotiFlow uses captured notification content for user-facing features such as:

- local notification history/inbox;
- rules-based categorization and priority;
- search, reminders, VIP rules, and digest summaries;
- optional OTP clipboard copying when the user explicitly enables it;
- optional transaction/expense derivation when the user explicitly enables it;
- optional local text-generation/classification only if a production-verified local model feature is enabled in a future release.

Notification Access is used for the notification-inbox product itself, not for advertising or sale of user data.

## On-device storage

Captured notification history is stored in NotiFlow's application-private SQLite database on the device. Derived expense records can include financial information from supported transaction notifications.

Android application backup is disabled by the app manifest. That does not mean the SQLite database is cryptographically encrypted at rest; the database is protected primarily by Android's application sandbox. The project must not claim stronger at-rest encryption unless it is actually implemented and verified.

## Off-device transmission

The production-hardening source has no cloud AI fallback and no automatic network model download. Rules-based classification works locally.

Before release, the final merged manifest, release APK/AAB, and all embedded third-party SDK behavior must be inspected to verify whether any user data is transmitted off the device. Google Play Data Safety answers must be based on that final observed build rather than source assumptions.

User-initiated export/share actions intentionally send data to a destination chosen by the user through Android's share/file flows. Those actions are initiated by the user and are separate from background transmission.

## Local AI

Local AI activation is currently unavailable in the production UI while model identity, format, integrity, license, storage, and runtime compatibility are being production-verified. NotiFlow does not automatically download an AI model.

If local AI is introduced later, this policy and the in-app disclosure must be updated before release if data handling changes.

## OTP clipboard feature

OTP auto-copy is off until the user explicitly enables it. When enabled, NotiFlow can place a detected verification code in the Android clipboard.

On supported Android versions the clipboard item is marked sensitive. That flag is a system presentation/privacy hint; it does not make the system clipboard an encrypted vault.

NotiFlow's own auto-copy confirmation notification does not repeat the OTP value.

## Expense/transaction feature

Expense parsing is off until the user explicitly enables it. When enabled, supported notifications can be parsed locally into derived transaction rows.

Turning the feature off stops parsing future notifications. Existing derived rows remain on the device until the user deletes local history. Derived transaction data is inferred from notifications and should not be treated as an authoritative bank/accounting record.

## Voice Reader

If the user enables the Voice Reader, eligible notification content can be spoken by Android Text-to-Speech according to the configured routing/filter policy. OTPs are excluded by the current policy.

Physical-device routing differs across wired, Bluetooth, USB, speaker, call-state, and OEM configurations; release claims must stay within device-tested behavior.

## Reminders and digests

NotiFlow can create its own reminder and digest notifications if the user grants Android notification-posting permission. Generated notifications use private visibility and generic public versions; sensitive preview settings further reduce displayed content where implemented.

Android WorkManager provides approximate background scheduling. NotiFlow does not promise exact-clock delivery.

## Excluded applications

Users can exclude source apps from future capture. The exclusion gate runs before NotiFlow analyzes or persists new notification content from that package.

Excluding an app does not automatically delete history that was captured previously. Existing local data must be deleted separately unless a dedicated “delete existing data” action is provided.

## Data retention and deletion

NotiFlow provides local-history deletion controls. “Clear all” removes NotiFlow's notification database content and derived expense rows and cancels reminder work associated with deleted history.

Additional retention/cleanup behavior may remove old low-value records according to app settings. Cleanup must protect pinned/VIP/reminder content according to the current implementation.

Before release, all deletion paths must be device/integration-tested for notification rows, expense rows, learned rules where applicable, and WorkManager jobs.

## Encrypted exports/backups

Backup export requires a user-provided passphrase; NotiFlow does not use a universal built-in backup password.

The current version-1 archive does not implement full notification-history restore. It must not be presented as a complete backup/restore system until staged validation and transactional restore are implemented and tested.

## Data sharing and sale

NotiFlow does not sell personal or sensitive user data.

The source does not intentionally share captured notification content with advertising or analytics services. This statement must be re-verified against the final release build and all transitive SDKs before publication.

## Children

The current product is not designed or validated as a child-directed service. If the Play target audience changes, Families/SDK requirements require a separate review.

## Security

Security measures currently include Android application sandboxing, disabled Android app backup, private components for the notification listener/receivers, immutable PendingIntents where used, sensitive clipboard marking where supported, guarded destructive notification cancellation, and authenticated AES-GCM for password-protected export.

No security mechanism eliminates all risk. Device compromise, root access, screen/notification exposure, clipboard behavior, and user-initiated sharing can affect confidentiality.

## Policy/contact information required before publication

Before this document becomes the published Play privacy policy, add:

- legal developer or company name matching the Play listing;
- monitored privacy/support contact;
- effective date and update date;
- public, active, non-geofenced, non-editable web URL;
- final verified Data Safety declarations;
- any third-party SDK/model notices required by the final build.

## User choices

Users can decline Notification Access and continue into the app with capture-dependent features unavailable. Optional OTP copying, expense derivation, notification posting, and future local AI capabilities are separate controls and must not silently override a user's explicit choice.
