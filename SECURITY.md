# Security Policy

## Supported versions
This repository is in **pre-release development**; no version is currently designated for production security support.

## Reporting vulnerabilities
Do not publish credentials, session tokens, private game account information, or exploit details in public issues.

**Before publication:** configure GitHub private vulnerability reporting / Security Advisories and add a verified security contact. Until then, use GitHub's private repository contact route.

## Security boundaries
- The game and sign-in flow belong to the third-party website; SEAF Companion does not independently authenticate users.
- Never include cookies, API keys, session tokens, signing keys, or user screenshots containing identifiers in public issues.
- A debug APK is not a production security guarantee; production signing secrets must be protected in a restricted CI environment.
- Do not treat Google Play Protect scanning as certification of the app or its third-party content.

## Reporting expectations
Please provide affected version, Android version, reproducible steps and impact, using redacted screenshots and logs. Do not send passwords.
