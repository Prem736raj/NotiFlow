# NotiFlow — Product Audit, Launch Gates, and Growth Roadmap

**Assessment date:** 21 October 2026 (Asia/Kolkata)  
**Repository:** Prem736raj/NotiFlow, branch main, inspected commit ef64d88d0585fc1a70a00845ba798dae7f939d77  
**Product:** Android app com.premraj.notiflow, version 0.1.0  
**Audience:** solo developer / early beta team  
**Status of this file:** actionable assessment and proposal; no production release, Google Play Console change, or hosted policy deployment is implied.

## 1. Executive decision

**Current product / engineering readiness: 5.9/10 (provisional).** There is a substantial working implementation, but crucial quality evidence is missing. **Production launch readiness: 3/10.** Two release gates are demonstrably unmet: the generated release bundle uses an Android Debug certificate, and the privacy policy link used in the app returns HTTP 404.

**Recommended product direction:** "Your private inbox for notifications that need action." Concentrate on dependable capture, finding missed alerts, putting important items into a follow-up queue, and delivering reminders. Keep banking/UPI insights optional and secondary. Differentiate through the *outcome* users get; "notification history", "rules", "offline", and generic "AI" by themselves are already claimed by competitors.

**Launch decision:** Do not submit the currently generated release AAB to a production track. Complete the P0 gates, then run a real-device beta before expanding acquisition.

**Implementation progress (21 October 2026, local changes pending commit):** The release-signing debug fallback was removed, a signing prerequisite added to release packaging, CI configured to build with a short-lived test-only certificate, the public-only Pages workflow added, and privacy links normalized to the intended `/privacy-policy/` URL. These are source changes awaiting final validation, commit and deployment. Production upload credentials, a publicly reachable hosted policy, the Play Console review, and physical-device sign-off remain incomplete; see [release preparation](RELEASE_PREPARATION.md).

### Evidence and confidence

| Signal | Finding | Confidence / limitation |
| --- | --- | --- |
| Kotlin/Compose project | Material 3, minSdk 24, targetSdk 37, local SQLite and WorkManager | Source inspected |
| Features | Notification listener, history, priority scoring, search, Later, reminders, digests, VIP/focus rules, exclusion/masking, optional expense parsing, voice reader, encrypted export | Source present; device behavior not comprehensively validated |
| Checks | testDebugUnitTest, lintDebug, bundleRelease completed successfully; 76 tests, 0 failures, 0 skipped across 11 suites | Prior run on this checkout, 21 October 2026 |
| AAB | app/build/outputs/bundle/release/app-release.aab, 6,401,184 bytes (approximately 6.4 MB bundle) | Built artifact inspected; this is not per-device download/install size |
| Release signing | AAB signing certificate subject CN=Android Debug | Confirmed via keytool inspection; unacceptable as the intended production upload identity |
| Privacy URL | In-app URL https://prem736raj.github.io/NotiFlow/privacy-policy returns 404; Pages site root, directory and .html variants also return 404 | HTTP checks on 21 October 2026; policy HTML exists in the repository but is not published at these URLs |
| Google Pages API | Unauthenticated public /repos/Prem736raj/NotiFlow/pages request returns 404 | Does not by itself prove the repository's Pages setting; independently confirms deployment/configuration needs owner verification |
| Generative AI | GemmaClassifier currently returns unavailable/fallback; ModelDownloader does not download a production model | Source inspected; no functioning model-backed summarization/classification |
| Device quality | Pixel/Google, Samsung/One UI and Xiaomi/HyperOS reliability; battery; notification listener recovery; reminder timeliness | **Unknown until physical-device tests** |
| Market | Google Play competitor listings and official competitor site consulted | Public listing claims, not independent side-by-side device benchmarks |

### Weighted product score

Scores describe **present state**, not future potential. They are an engineering judgment with lower confidence for experience/performance areas lacking device evidence.

| Dimension | Weight | Score / 10 | Weighted points | Reason |
| --- | ---: | ---: | ---: | --- |
| Core feature completeness | 20% | 8 | 1.60 | Large functional set in source |
| Reliability and testing | 20% | 6 | 1.20 | 76 unit tests/lint pass; real-world capture and OEM testing missing |
| Privacy/security | 15% | 7 | 1.05 | Local processing, consent controls, encrypted exports; release/disclosure gates and restore gaps |
| UI/UX | 15% | 6 | 0.90 | Many screens, but first-use comprehension and current screenshots need testing |
| Differentiation | 10% | 6 | 0.60 | Reminders + private notification workflow promising, category crowded |
| Performance | 5% | 5 | 0.25 | Small bundle, no battery, database growth or latency measurements |
| Generative AI capability | 5% | 1 | 0.05 | Stub/fallback only |
| Play release readiness | 10% | 2 | 0.20 | Debug signing and broken hosted policy |
| **Total** | **100%** | | **5.85 → 5.9/10** | Provisional |

Separate **launch readiness 3/10** is a gate assessment and is **not** the weighted average. Targets: 7+/10 after P0 and device proof; **8+/10 is conditional** on demonstrable user benefit, retention and stability. Do not promise a score by a fixed date.

## 2. Market, competitive reality, and naming risk

| Alternative | Publicly described strengths | What this means for NotiFlow |
| --- | --- | --- |
| BuzzKill | Sophisticated rules, cooldown, batching/snooze, custom alerts and optional automation; explicitly privacy-first/offline | Rules and offline privacy alone are insufficient differentiation. Emphasize a simple actionable inbox and trustworthy reminders rather than matching its whole automation surface. |
| FilterBox | Searchable history, recovery, offline AI filtering and personalized rules; 100K+ installs shown on Play at research time | Be precise about the actual rules-based engine; do not market an AI filtering feature that is not shipping. |
| Notisave | Saves and searches notifications, including messaging history; established large user base | History alone is a commodity. Offer clarity, follow-up and quality of experience. |
| Android system features | Notification history and per-app notification/channel settings available on supported Android versions | Explain the practical extra value on the first screen: search, actionable Later queue, reminders and private insights. |

**Brand/search discoverability risk:** Other Google Play apps already use names including "NotiFlow", "Notiflow", and "Notification History: NotiFlow", in different packages. This is observable **name collision**, not a finding of trademark infringement. Before investing in artwork/ASO, check Play search in target markets, domain/handle availability and any trademark conflicts. A descriptive store title such as **"NotiFlow: Important Alerts"** can clarify purpose if branding remains. Seek legal advice if trademark clearance is relevant.

**Suggested customer starting segments (test, do not assume):**

1. Busy students/professionals who miss important alerts among chats and promotions.
2. Android power users who want a locally controlled searchable inbox.
3. Users who want opt-in reminders for bill/payment/delivery/meeting alerts, after high-accuracy parsing is proven.

Interview 10–15 users from the first segment before adding more major features. Observe their past missed-notification incidents, the applications involved, current workarounds, willingness to grant access and what a successful reminder would have prevented. Validate the problem before creating new AI features.

## 3. P0 — Release safety and compliance (week 0–2; approximately 12–25 focused hours)

No dates are guarantees; sequence assumes a solo developer working part time and depends on external Play review, device access, and hosting configuration.

| ID | Work item | Specific implementation / verification | Definition of done |
| --- | --- | --- | --- |
| P0-01 | Production signing | Remove the silent Android Debug-key fallback from app/build.gradle.kts. Require explicit upload-key path, alias and passwords for production signing; keep debug test builds separate. Do not generate or commit a key/password. Verify bundle certificate with keytool. | Release tasks fail clearly without production credentials and a signed release has the expected **non-debug** upload certificate; upload key backed up securely. |
| P0-02 | Publish policy site | The policy source exists at docs/privacy-policy/index.html and docs/privacy-policy.html, but **every tested prem736raj.github.io/NotiFlow route is 404**. Configure a real GitHub Pages deployment for docs/ (or a reliable alternative), then check public URL on mobile/incognito. | Public policy page returns HTTP 200 via HTTPS; homepage links resolve; app's exact URL and Play Console URL match an accessible, final policy. |
| P0-03 | Privacy text and disclosure | Review actual notification, OTP, sender, financial, clipboard and archive behavior against policy; verify visible disclosure *before* the notification access permission flow; ensure affirmative consent and decline paths. | Reviewer can see what access reads, why, local processing and sharing/retention rules; policy and Data Safety declarations agree with the shipped binary. |
| P0-04 | Play Console readiness | Check current target API rules, access declarations, content rating, Data Safety, store listing, developer contact, permissions, signing/enrollment and app access; review whether a 12-tester/14-day closed-test requirement applies to the developer account. | All relevant Console checklist items complete; tester access and review flow documented; no contradictory marketing claims. |
| P0-05 | Safety regression on devices | On ≥3 device/OEM classes, grant/revoke notification access; receive OTP and payment notifications; enable focus; create/delete reminders; verify locked-screen previews and voice behavior. | No dangerous loss of OTP/payment alerts; no sensitive previews/voice disclosures in disallowed states; critical paths recorded with reproducible results. |
| P0-06 | Release identity | Confirm versionCode/versionName, Play App Signing upload certificate and an install test through internal/closed track using the actual candidate artifact. | Signed AAB accepted by target testing track, installs and launches on test devices; no unexpected permission or native ABI issues. |

**Policy caveat:** Automatically uploading notification content or finance information for analytics/debugging would materially change the privacy/security posture. The Google Play User Data policy explicitly demands adequate disclosure and consent for sensitive content. Do not introduce that collection to measure growth.

**P0 verification commands** (examples; replace production credentials outside source control):

~~~powershell
# Expected final production AAB, after secure signing setup:
.\gradlew.bat testDebugUnitTest lintDebug bundleRelease
keytool -printcert -jarfile app\build\outputs\bundle\release\app-release.aab
Invoke-WebRequest -Uri "https://prem736raj.github.io/NotiFlow/privacy-policy/" -UseBasicParsing
~~~

The policy URL shown here is the intended directory route; it is **currently broken**. Do not treat this command as a success until the deployment is corrected.

## 4. P1 — Reliability, performance, and trust (weeks 3–6; 25–45 hours)

| Priority | Change | Verification / acceptance target |
| --- | --- | --- |
| 1 | Listener lifecycle hardening: when disconnected, show access health, respect Android's onListenerDisconnected semantics, and consider safe requestRebind with throttling and no battery-hungry polling | Across reboots, force stops, revoked permission and system recovery, users see an honest status and capture resumes when Android permits |
| 2 | Device matrix: Pixel/AOSP, Samsung/One UI, Xiaomi/HyperOS, Android API levels 24/29/33/35/37 when available | Log coverage gaps; ensure manufacturer battery restrictions are explained without implying bypass guarantees |
| 3 | Reminder/digest reliability: distinguish flexible WorkManager windows from exact alarms; test timezone/DST, reboot, Doze, permission denial and repeated schedules | Report actual delivery delay distributions; no claim of minute-exact delivery from periodic WorkManager |
| 4 | Classification fixtures: collect **synthetic or explicitly consented/redacted** OTP/payment/chat/promotional samples; measure false suppression and mistaken finance entries | Zero known unsafe focus cancellation on protected classes; no automatic finance totals for low-confidence messages |
| 5 | Security/export: update password strength guidance, archive format/version tests; decide whether to ship validated import/restore or label as export-only | Corrupt/wrong-password archives never produce success; user can tell precisely what is backed up |
| 6 | Instrument observability privately: on-device health dashboard (listener active, last seen event, work status, local storage) and consent-based diagnostics that **never include notification text** | Testers can identify broken capture and submit redacted issue reports |
| 7 | Performance profiles: cold start, scrolling with 1K/10K/50K history rows, query latency, memory, battery, WorkManager frequency, database retention growth | Establish baselines; no unbounded storage/CPU growth; regressions captured in release notes |
| 8 | Accessibility and permission UX: font scaling, screen-reader labels, contrast, small-screen layouts, permission revoked state | Core actions usable at 200% font size and with TalkBack; no silent dead ends |

**Test funnel:** installation → sees value → understands Notification Access → grants access → first alert captured → first useful Later action → reminder delivered → returns within seven days. Record reasons for failures, not merely button clicks.

## 5. P2 — Product experience and differentiation (weeks 7–12; 30–55 hours)

**Home:** Show one outcome-focused default screen. Recommended groups: **Important**, **Later**, **Everything**. Keep Deleted/History, Expenses, Insights, and advanced rules accessible without flooding the first-time navigation. Validate hierarchy through usability tests before a large Compose refactor.

**Most valuable interaction:** Long-press or swipe a notification to **Remind me** (e.g. tonight, tomorrow morning, choose time), **Done**, **Pin/VIP**, or **Mute this kind** with a confirmation/undo. Demonstrate from a captured alert to a successful reminder in under 30 seconds during a usability test.

**Explain classification:** Display "Important because VIP", "Likely transaction", or "Promotion based on app/content pattern", plus a direct correction action ("Always important", "Never categorize as expense"). Show confidence or uncertainty for expense parsing, and provide manual edit. Keep learned corrections reversible.

**Templates rather than complicated automation:** "Keep bank and OTP alerts visible", "Batch promotions", "Remind me about delivery updates", "Work hours". Every preset needs opt-in, preview, cancel/undo and fail-safe behavior. Avoid interruptive auto-actions by default.

**Make insights actionable:** Examples are "3 important alerts waiting", "2 reminders due today" or "X alerts from muted sources this week". Avoid vanity charts without a next action. Keep finance insights separate and explicit opt-in; test false-positive and duplicate-transaction handling.

**Quality gate:** 10 new-user usability sessions, task completion rate for first successful reminder, and logged top-five misunderstandings. Ship the highest-friction fix before adding another dashboard.

## 6. AI investment gate (after reliable baseline; usually month 3+)

The repository currently ships a **rules-first experience**. GemmaClassifier is a fallback stub, and there is no operational production model download. Do not use "generative AI powered" in the listing yet.

Run a simple comparative evaluation of **rules only** vs a candidate **small on-device model** on redacted, consented examples: relevance/priority accuracy, OTP protection, transaction false positives, median/p95 inference time, battery drain, RAM peak, total model download size, license, supported ABI, model integrity and offline fallback. For model download, use a clear explicit consent/download screen and checksum verification. Keep the rule classifier available on all devices.

**Decision rule:** Ship a model only if it makes the users' top problem measurably better on affordable devices without compromising privacy or reliability. Otherwise invest in rules and interaction design; the product can succeed without generative AI.

## 7. Growth plan: audience → activation → retention → acquisition

### Before public promotion: private beta (weeks 1–6)

- Recruit 15–30 **relevant** users, across at least three OEMs, and conduct 10 interviews; include more than the Play minimum where that rule applies to the account.
- Provide a concise test script: enable listener, wait for real notifications, find an older item, mark Later, receive reminder, try opt-out/delete, report capture issues.
- Use Play internal/closed testing; if the account falls under new personal-account rules, satisfy **at least 12 opted-in testers continuously for 14 days** before applying for production access. Check the Console account-specific requirements.
- Get qualitative outcome evidence: "This prevented me missing X" vs "I granted access but never used Later." Reject expansion until the most common failure has an owner.

### Store launch (weeks 6–12 after gates)

- Value proposition: **"Never lose the alerts that need a follow-up."** The screenshots should demonstrate the actual flow, not a generic AI promise.
- Listing title candidate (under 30 characters): **NotiFlow: Important Alerts**. First short-description candidate (under 80 characters): **Save important alerts, set reminders, and find notifications privately.**
- Screenshot storyboard: 1) Find an important missed alert, 2) Save to Later, 3) Get a reminder, 4) Search history, 5) Stay in control of sensitive data. Only use screens that work in the release candidate.
- Keyword *hypotheses* to validate with Play Console search terms and competitor discovery: "notification history", "notification organizer", "notification reminder", "missed notifications", "notification manager", "private notification history". Search intent and competition need Console data before selecting title language.
- Start with English and a user base you can support; test Hindi localization when users demonstrate demand, including accurate disclosure text and screenshot translations.
- Reply helpfully to support requests and authentic reviews. Ask for voluntary feedback only after a user has experienced a successful reminder; never offer incentives for ratings.
- Once there is enough store traffic for a meaningful statistical read, run one-variable listing experiments on screenshots/icon/short description, then apply winners. Google Play exposes experiment confidence and sample size guidance; low traffic may produce no decision.

### Sustainable organic distribution (months 3–6)

Run a six-week publishing cycle: two concise demo videos per week ("notification I nearly missed", "Later workflow"), one more detailed tutorial every two weeks, and one useful community contribution every two weeks where promotion is allowed. Publish a clear landing page with privacy explanation and demo GIF/video only after hosted URLs work.

Channels to test separately: YouTube Shorts and Android tutorial videos, an educational GitHub README/demo, relevant Android/productivity forums, student developer communities, and referrals from genuinely satisfied testers. Avoid posting private notification screenshots unless synthetic. Give each campaign its own tagged landing/listing URL and compare **activated users**, not raw clicks. Stop channels that acquire permission-denying or nonretained users.

**No paid user acquisition initially.** Consider a limited spend only once install-to-activation and week-one retention are measured, support load is manageable, and the Play listing converts consistently.

## 8. Metrics and experiment framework

Definitions matter more than an impressive download count. Use Play Console aggregate acquisition, retention, Android vitals and opt-in tester feedback as the default. For any additional analytics, get deliberate consent and transmit **only coarse counters**, never notification title/body, sender, OTP, finance content or source details that could identify a person.

| Metric | Definition / calculation | Initial internal decision target* |
| --- | --- | --- |
| Permission activation | Fresh installers who complete Notification Access disclosure and enable access within first day / eligible first opens | ≥60%, measured alongside decline reasons |
| First value | New users with at least one captured eligible alert and one explicit useful action within 48 h / eligible new users | ≥40% |
| Reminder success | Reminders delivered within the **stated** scheduling tolerance / reminders due on supported test devices | ≥95%; publish tolerance honestly |
| Capture reliability | Listener-observed test notifications persisted correctly / eligible test notifications posted in controlled runs | ≥99% in lab matrix; exclude OS-inaccessible events |
| D1 and D7 retention | Install cohort active on day 1 and day 7 respectively / cohort installs; use one reporting definition consistently | D7 ≥25% as a **hypothesis to test**, not an industry benchmark |
| Weekly value | Weekly activated users who complete a Later action, reminder, retrieval or protected-focus event | Trend increasing with qualified users |
| Store conversion | First-time installations / eligible Play listing visitors, segmented by country/source | Establish baseline and experiment only with enough traffic |
| App quality | Play user-perceived crash/ANR rates, sorted by affected users and device | Stay materially below Play bad-behavior thresholds; trend to zero |
| Support latency | Median time to first response for a reported capture, privacy or data-loss issue | Respond within 2 working days while beta is small |

*These are **planning thresholds chosen for this project**, not observed NotiFlow metrics or universally accepted benchmarks. Recalibrate after the first 30–50 genuinely activated beta users.*

Suggested event taxonomy **only if explicit opt-in is implemented**: disclosure_shown, permission_enabled, capture_verified (no raw event content), notification_marked_later, reminder_scheduled, reminder_delivered, notification_retrieved, listener_disconnected, permission_revoked. Prefer counting on-device and asking testers to share aggregate reports. Never send content for "debugging" by default.

Experiment backlog, in order:

1. **Activation:** Plain-English disclosure + demo before permission vs current onboarding. Primary metric: permission activation *and* first value; guardrail: informed consent/revocation.
2. **First value:** Home opens to Important/Later vs existing default. Primary metric: first useful action within 48 h; guardrail: discovery of full history.
3. **Reminder affordance:** Persistent Later prompt vs swipe-only action. Primary metric: successful reminder completion; guardrail: unintended reminders.
4. **Store message:** "Never miss alerts" screenshots vs "Private notification history" screenshots. Primary metric: install/open conversion; guardrail: activation after install.
5. **Retention:** Weekly on-device "items worth following up" prompt (strictly opt-in) vs no prompt. Primary metric: weekly value; guardrail: notification annoyance/uninstalls.

Do not A/B test too many changes simultaneously or overinterpret small samples. In low traffic, use ten-user task observations instead of claiming a statistical lift.

## 9. Revenue strategy and resource budget

**First 90 days:** optimize for trust and repeat value, not ad impressions. Notification bodies and financial alerts make ad-supported monetization particularly risky to product perception. Keep a generous baseline free and evaluate monetization after retention is real.

Potential later option: a one-time upgrade for advanced rules/presets, extended history management or enhanced workflows, while preserving local privacy controls and basic capture/reminders. Before setting price, run interviews on willingness to pay and verify Play Billing/consumer-law requirements. Avoid paywalling data deletion or other necessary privacy controls.

At approximately **10–15 development hours per week**, P0 + cross-device stabilization + focused UX improvements can consume 12 or more weeks. Allocate about 60% engineering, 25% device/user testing, 15% listing/content/feedback early on. Avoid launching AI, complex finance automation and monetization at the same time.

## 10. Risks, dependencies and release decision

| Risk | Impact | Mitigation / owner action |
| --- | --- | --- |
| Debug-signed release | Play signing identity failure / unsafe release discipline | Configure actual upload key; forbid silent fallback; verify the final certificate |
| Policy URL unreachable | Trust and Play compliance risk | Publish docs/ policy via Pages or alternate host; verify public 200 before changing in-app link |
| Notification Access denial/revocation/OEM kill | App becomes useless or misleading | Health UI, clear permissions support, field matrix, safe recovery |
| Quieting important alerts | User harm, lost trust | OTP/payment/VIP protections + high-quality tests and undo |
| Incorrect finance parsing | Misleading totals | Explicit opt-in, confidence, source reconciliation, manual corrections |
| Storage/privacy leakage | Sensitive content exposure | Local-only design, masking, data minimization, archive versioning and careful support logs |
| Overcrowded interface | Low first-value and retention | Focused actionable inbox, usability research, simplify defaults |
| Generic brand/search collision | Harder ASO and accidental app confusion | Distinct descriptor/creative; search and naming clearance |
| Overclaiming AI | Store policy/user trust | Rules-first marketing until actual model ships and passes quality gates |
| Poor retention despite installs | Wasted content or ad spend | Prioritize weekly value and D7 before scale |

### Go/no-go checklist

**Go to controlled beta** only when: private-signing flow documented and checked; working public privacy URL; reviewed disclosure/data practices; devices capture and retain events safely; at least one end-to-end reminder verified; no high-severity data-loss or sensitive disclosure issue.

**Go to broad production** only when: Play review prerequisites complete; real candidate AAB installed from testing track; minimum required tester period passed where applicable; cross-OEM regression green; critical bug backlog empty; at least one group of beta users describes a repeatable user benefit; rollout and rollback process documented.

### Exact next steps for the maintainer

1. **Set up publishing:** choose GitHub Pages deployment for docs/ or another HTTPS host. Verify that https://prem736raj.github.io/NotiFlow/privacy-policy/ returns 200; until then the current policy URL is broken. Update Play Console and in-app URL only to a verified public endpoint.
2. **Secure signing:** provision and back up an upload keystore outside Git; remove debug fallback; make missing release credentials fail; verify keytool certificate and test AAB acceptance.
3. **Recruit beta:** run a two-week controlled test with real Android devices, including Samsung/Xiaomi and consent flows. Check whether the developer account has a mandatory 12-person/14-day requirement.
4. **Ship health and reminders:** listener status/recovery, honest scheduling copy and follow-up UX before major new features.
5. **Measure activation and value:** run user tests and build the first Play listing around "actionable important alerts"; postpone AI/payments expansion until evidence warrants it.

## 11. Sources and revalidation

Research checked on 21 October 2026. External listings and policies change; re-open before shipping or publishing copy.

- [BuzzKill official product](https://buzzkillapp.com/) and [BuzzKill on Google Play](https://play.google.com/store/apps/details?id=com.samruston.buzzkill)
- [FilterBox on Google Play](https://play.google.com/store/apps/details?id=com.catchingnow.np)
- [Notisave on Google Play](https://play.google.com/store/apps/details?id=com.tenqube.notisave)
- [Notification History: NotiFlow — another app using the name](https://play.google.com/store/apps/details?id=com.namslab.thisthat)
- [NotiFlow: Leitor Notificações — another app using the name](https://play.google.com/store/apps/details?id=com.eloi.notiflow)
- [Google Play: User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play: Store listing requirements](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Google Play: Store listing best practices](https://support.google.com/googleplay/android-developer/answer/13393723)
- [Google Play: Store listing experiments](https://support.google.com/googleplay/android-developer/answer/12053285)
- [Google Play: Testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Google Play: Android vitals](https://support.google.com/googleplay/android-developer/answer/9844486)
- [Android: NotificationListenerService and requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- Local technical evidence: app/build.gradle.kts; app/src/main/AndroidManifest.xml; app/src/main/java/com/premraj/notiflow/intelligence/GemmaClassifier.kt; app/src/main/java/com/premraj/notiflow/util/BackupExporter.kt; docs/PLAY_DATA_SAFETY_NOTES.md; docs/PHASE_STATUS.md; AUDIT_FINDINGS.md.

**Do not interpret competitor descriptions or developer self-reported Play Data Safety labels as independent verification of their implementation.** NotiFlow device stability, live retention and actual store conversion remain unknown until measured.
