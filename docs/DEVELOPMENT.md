# Development

## Requirements
- JDK 17
- Android SDK and platform/build tools matching `app/build.gradle`
- Gradle 8.11.1 (CI provisioned by GitHub Actions)

## Build
```bash
gradle --no-daemon assembleDebug
```

The CI workflow downloads Android command-line tools, installs SDK packages, builds and verifies an APK, then uploads an artifact.

## Testing
1. Verify initial load and login, including third-party redirects.
2. Rotate portrait/landscape and check game accessibility, companion overlay and status-bar clearance.
3. Expand/collapse companion controls.
4. Schedule a 1-minute reminder, background the app and measure scheduled-vs-delivered notification time.
5. Test notification tap-to-open, sound/vibration settings, and cancellation.
6. Reboot with a future timer to verify restoration.
7. Install an upgrade APK with the same signing key and higher version code; verify WebView session preservation.
8. Verify app behavior with no connectivity and notification permission denied.

## Current limitations
- Manual timers are **not synchronized** to actual SEAF missions.
- Android inexact alarms may arrive late due to Doze and other OS scheduling.
- Notification-channel sound is user-controlled once a channel is created.
- No independent third-party website security audit has been performed.
- Do not commit private keystores or authentication material.

## CI artifact
Use `git` / GitHub Actions for source review. Never present a debug APK as a verified production release.
