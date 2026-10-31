# Production release checklist

**Status: BLOCKED — not ready for public publication.**

## Legal and branding
- [ ] Obtain permission for using SEAF trademarks, logos, in-game content, and any derived screenshots in marketing.
- [ ] Replace/remove the Helldivers-labelled sound unless redistribution rights are documented.
- [ ] Decide on a non-impersonating public app name and original icon if permission is unavailable.
- [ ] Select and document a source-code license only after asset and ownership review.

## Android requirements
- [ ] Update compile/target SDK to at least API 36 for new Google Play submissions as of October 2026 (verify current Play policy at release).
- [ ] Regression-test Android 16/17 behavior, third-party login, insets, background alarms and notifications.
- [ ] Use a **dedicated production keystore** securely managed outside the repository; never use CI debug keystore for production.
- [ ] Add signed production Android App Bundle (AAB) build and signing verification.
- [ ] Set semantic version names and strictly increasing version codes; preserve production signing identity permanently.
- [ ] Verify SHA-256 signing fingerprints before distributing updates.
- [ ] Avoid publishing debug APKs as releases.

## Trust and security
- [ ] Publish a complete privacy policy on a public HTTPS URL, linked both in-app and in Play Console.
- [ ] Audit third-party data flows and complete Play Console Data Safety declarations.
- [ ] Add publisher/support contact and private vulnerability disclosure.
- [ ] Enable dependency scanning, secret scanning and code review protections as appropriate.
- [ ] Review Android permissions and minimize to what is essential.
- [ ] Validate API usage, secure navigation, WebView behavior, and handling of third-party authentication.
- [ ] Run functional, accessibility, and device testing; conduct independent security review.

## Store listing
- [ ] Real, privacy-reviewed screenshots for portrait and landscape.
- [ ] Original licensed launcher icon, feature graphic and description.
- [ ] Clear indication that this is an **unofficial** companion, not a game publisher's app.
- [ ] Complete content rating, app access instructions, audience and data disclosures.
- [ ] Complete Google Play's applicable testing/verification procedures and obtain listing approval.

## Distribution
Do **not** consider a green GitHub Actions build proof of Play policy compliance. Perform the preceding checks before distributing a public APK/AAB.

References: [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878), [Play review preparation](https://support.google.com/googleplay/android-developer/answer/9859455).
