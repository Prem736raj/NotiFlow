# NotiFlow release preparation

This is the release control process for the October 2026 P0 work. Local code changes do not by themselves publish a website or approve a Google Play release.

## Production upload-key setup

bundleRelease and assembleRelease require four explicitly configured values. The build fails if any are missing, the keystore path is invalid, or the keystore is the Android Debug keystore.

| Gradle property / environment variable | Purpose |
| --- | --- |
| KEYSTORE_PATH | Absolute path to a dedicated upload JKS outside Git |
| KEYSTORE_PASSWORD | Keystore password |
| KEY_ALIAS | Dedicated upload-key alias |
| KEY_PASSWORD | Key password |

Store real values in a private Gradle user-home gradle.properties file, or inject them as environment variables in a private release pipeline. Do not commit passwords, keys, or screenshots of credentials. Do not use the Android Debug keystore. Keep a secure backup of the correct upload key and recovery information. For an existing Play Console application, use the enrolled upload key.

Clear old build outputs because a previous AAB used a Debug certificate, then build:

~~~powershell
.\gradlew.bat clean testDebugUnitTest lintDebug bundleRelease
keytool -printcert -jarfile app\build\outputs\bundle\release\app-release.aab
~~~

Verify the final certificate subject and SHA-256 fingerprint against the intended production upload identity registered in Play Console, and reject CN=Android Debug. Install the candidate from an internal/closed Play testing track before promoting it.

GitHub Android CI uses a deliberately short-lived CI-only certificate. Its AAB is a build test and must never be used for a Play release.

## Publish the privacy policy

The app embeds the privacy policy and does not require an external URL. The optional public homepage and policy are published at:

**https://prem736raj.github.io/NotiFlow/privacy-policy/**

The privacy source is docs/privacy-policy/index.html. The Pages workflow at .github/workflows/pages.yml uploads only the public homepage and policy HTML; it excludes audit notes, development prompts and other internal docs.

1. In GitHub repository Settings → Pages, choose **GitHub Actions** for Build and deployment. Check the repository/account supports Pages.
2. Push the reviewed workflow and public HTML to main, or manually dispatch the **Publish NotiFlow policy website** workflow after it has been committed.
3. Verify the deployment job succeeds. Open homepage and policy from an incognito/mobile browser, and verify both return HTTP 200.
4. Review policy accuracy, developer contact and effective date against the final binary. Configure the same **working** URL in Play Console.
5. If repository name or domain changes, update the Play Console URL and the optional public site; the in-app policy remains self-contained.

**At implementation time:** GitHub Pages API and the public URL returned HTTP 404. Adding this workflow locally has not created a deployed page. P0-02 is incomplete until HTTP 200 is observed.

## Remaining Play/device checks

- [ ] Verify consent, sensitive-data disclosure, and decline/revocation on physical devices.
- [ ] Review final merged manifest, SDKs, Data Safety declarations and current Play submission rules.
- [ ] Test capture, listener recovery, OTP/payment protection, reminders, app lock, and lock-screen privacy on Pixel/AOSP, Samsung and Xiaomi/HyperOS.
- [ ] Verify upload-key identity and final AAB acceptance on the Play testing track; fulfill account-specific testing requirements.
- [ ] Record performance, scheduling tolerance, battery and crash/ANR baselines before broad release.
