# Google Play Data Safety / Policy Notes — NotiFlow

**Status:** release checklist, not a submitted Play Console declaration.

Google Play defines “collected” for Data Safety as data transmitted off the user's device. Data that is accessed and processed only on-device does not need to be declared as collected solely for that reason. This is different from the broader privacy-policy obligation to disclose sensitive data that the app accesses and stores locally.

## Current source-level assessment

| Data / capability | Current source behavior | Data Safety implication to verify in final build |
|---|---|---|
| notification title/body/sender/app | accessed and stored locally after Notification Access | if never transmitted off-device, not “collected” under Data Safety's off-device definition; still disclose in privacy policy/prominent disclosure |
| OTP content | stored as part of notification history; optional clipboard copy | verify no SDK/transmission path; clipboard is on-device |
| financial/payment notification content | local notification storage | sensitive financial information; must not be publicly exposed |
| derived expense data | created locally only after explicit opt-in | verify final release has no transmission/export except user action |
| local AI prompt text | local MediaPipe path only; activation UI disabled | verify final SDK/runtime build has no telemetry/network behavior |
| backup/export | encrypted text shared only after user action | user-initiated transfer destination must be clear |
| reminders/digests | posted locally through Android | notification previews require privacy testing |
| app/package identity | used locally for source/exclusion/classification | inspect all SDKs for off-device package inventory behavior |
| analytics/ads | none intentionally integrated in current app dependencies reviewed | verify transitive/release APK before answering “No” |

## Prominent disclosure gate

Before opening Android Notification Access, NotiFlow should clearly explain that it can read notification content from enabled apps, including messages, verification codes, and banking/payment alerts; why that access is needed; how the content is used; and whether it is shared.

The onboarding hardening branch now places this disclosure immediately before the button that opens Notification Access settings. Device/reviewer-flow verification is still required.

## Final APK/AAB verification

Before completing the Play Data Safety form:

1. inspect the merged release manifest;
2. inspect final release dependencies and Play SDK Index entries;
3. search final code/resources for analytics, advertising, telemetry, crash upload, HTTP clients, and unexpected INTERNET permission;
4. inspect MediaPipe/LiteRT runtime behavior used by the actual release;
5. exercise all network calls on a clean physical device;
6. verify user-initiated export destinations are the only intentional off-device path for notification content;
7. update the privacy policy and Data Safety form together.

## Play policy notes

Current Google Play User Data policy requires accurate privacy-policy and Data Safety disclosures and prominent in-app disclosure/affirmative consent when sensitive data access may not be reasonably expected. Financial/payment information is personal and sensitive data and must not be publicly exposed.

Requests for permissions/APIs that expose sensitive information must be necessary for current user-facing functionality and used only for consented purposes. Do not request or market sensitive access for unimplemented features.

## Release-blocking Play tasks

- [ ] publish privacy policy at a valid public URL and expose it in-app;
- [ ] add legal developer/publisher identity and monitored privacy contact;
- [ ] record a reviewer flow showing notification-access disclosure and consent/decline behavior if requested by Play;
- [ ] complete final Data Safety form from the release APK/AAB, not this source note;
- [ ] verify SDK data practices / Play SDK Index;
- [ ] verify target API and current Play submission requirements at submission time;
- [ ] ensure store listing does not claim “Delete for everyone” detection, exact expense accuracy, semantic AI search, automatic driving detection, or production-ready AI model support unless those claims become true and are tested.

## Official references

- User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Prominent disclosure best practices: https://support.google.com/googleplay/android-developer/answer/11150561
- Data Safety form definitions: https://support.google.com/googleplay/android-developer/answer/10787469
- Permissions/APIs accessing sensitive information: https://support.google.com/googleplay/android-developer/answer/16558241
