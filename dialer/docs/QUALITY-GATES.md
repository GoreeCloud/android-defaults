# GoreeCloud Dialer — Quality and Device Acceptance

**Status (2026-11-09):** Android development preview, **not** certified as a replacement emergency dialer, physically verified release, or signed delivery.

## Automated gates

The pinned CI workflow at [Android Dialer CI](../../.github/workflows/dialer.yml) runs a **JDK 17 / Android SDK 35 / Gradle 8.13** build and static privacy policy checks:

- Unit tests: phone-number validation and T9 behavior.
- Android Lint, debug assembly, and packaged APK name/Internet-permission checks.
- Non-exported notification action receiver, system-bound `InCallService`, no `INTERNET`, `READ_SMS`, or `WRITE_CALL_LOG` permission, and explicit cloud/device transfer exclusions.
- Disposable Android 26 and Android 35 emulator instrumentation: offline 4-tab navigation, `tel:` deeplink, package permissions, and local Favorites persistence/cleanup.
- Emulator screenshot artifacts include the rendered default keypad and 1.5× Android system font-scale keypad on API 26/35, plus an Android 35 system dark-mode keypad. The job verifies the keypad heading **and visible primary Call control** in Android's accessibility hierarchy before capture. At large font sizes, the Call action remains pinned above the floating dock while the dial keys scroll. Screenshots must be opened and reviewed before any visual-acceptance assertion.

**Limits:** These tests do not initiate cellular calls or emulate real OEM/telecom/carrier routing. A green CI run is not call-quality or emergency-readiness certification.

## Physical-device test matrix (required before release)

Use a secondary, explicitly authorized Android test phone with rollback in
**Settings → Apps → Default apps → Phone app**. Do not replace the everyday
emergency-calling device while preview validation is incomplete.

| Area | Acceptance check |
| --- | --- |
| Role/permissions | Role request, refusal, removal and regrant; denied Contacts, Call Log, and Notifications permissions |
| Calling | Outgoing/incoming carrier calls, hangup, ringtone, caller ID presentation, hidden/private numbers, call waiting |
| Controls | Answer, decline, mute, hold/resume, DTMF, speaker/wired/Bluetooth endpoint changes |
| Dual SIM/conference | Choice of account, call swap, add-call and conference merge only when platform advertises support |
| System interaction | Lock screen, full-screen call UI restrictions, background/foreground transitions, notification channel changes, process recreation |
| Compatibility | Android 8.0/8.1, Android 12+, Android 14+, OEM power management and platform API 34+ endpoint handling |
| Accessibility | TalkBack, large text, high-contrast themes, labels/focus, landscape and tablet widths |
| Privacy | App has no Internet permission; no names/numbers persisted by Favorites; backup/cloud and cross-device exclusions; masked withheld numbers |
| Emergency routing | **Documentation review and approved provider test protocols only; no unsolicited calls to emergency services** |

Capture device model, Android version, SIM/carrier, role state, tested steps,
expected/actual results, logcat redacted of phone numbers, screenshot evidence
where consented, and rollback outcome. Never upload real contact or call-log data
to public issues, CI logs or screenshot artifacts.

## Further engineering risks

- Contact and call-log provider reads are now serialized on a background
  executor with bounded cursor scans, actual Android provider cancellation signals
  and request-generation guards.
  **Performance acceptance is still open**: the current emulator suite does not
  inject slow/malformed providers or large data sets, and device jank has not
  been measured. Query caches are in-memory and invalidated on Activity resume.
- Optional Favorites use Android contact IDs. IDs may be reassigned after
  contact-provider migrations. The app retains only local IDs; its Settings
  screen can clear them. Verify migration behavior on real devices.
- Audio endpoint and conferencing paths are gated on Telecom platform support,
  but need real hardware acceptance. The platform may refuse a route change.
- AI screening, PSTN recording, live translation, voicemail and spam blocking
  remain out of scope without separate role, consent, platform and safety review.

## Evidence discipline

Record exact PR head and GitHub Actions run URL for each result.
A CI file transport ZIP is not a signed standalone APK distribution.
A source change is not a tested, shipped, or device-approved capability.
