# GoreeCloud Dialer — Android development preview

An independently buildable **native Kotlin Android** phone app in the `GoreeCloud/android-defaults` repository. This is a functional source implementation proposal, **not a certified default-phone replacement**, signed release, or validated physical-device deployment.

## Scope implemented in source

- Android `ACTION_DIAL` handler, keypad with long-press +, number validation, system telephony hand-off, and request to become default Phone app through `ROLE_DIALER`.
- `InCallService` integration, privacy-conscious incoming/ongoing call notification, full-screen incoming activity where the OS permits it, answer/reject/end, mute, speaker, hold/resume, and DTMF.
- Optional, on-device contact discovery with T9 name/number matching, contact dialing and SMS hand-off; optional last 100 call-history entries.
- Light/dark app-local GoreeCloud UI semantic mappings, touch-friendly native controls, labels, and in-call state handling.
- GoreeShield: no Internet or trackers, no recording, no background contact uploads, opt-in runtime access to contacts/call history, cloud/transfer backup exclusion.
- Wardveil: signature/system-bound `BIND_INCALL_SERVICE`, non-exported notification receiver, input normalization, and no direct user-data network egress.

## Build

Requires JDK 17, installed Android SDK 35, and Gradle 8.13:

```bash
cd dialer
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The debug APK is `dialer/app/build/outputs/apk/debug/app-debug.apk`. It is a development build, **not** a standalone APK delivered through GoreeCloud/Artifacts or a phone-tested release. The repository's GitHub Actions workflow validates source policy, tests, assembly and lint. CI archives are not standalone signed-delivery confirmation.

## Device use and safety

Install on a **non-primary test phone** with a working carrier account. Launch; choose **Make default phone app** in Settings, then grant necessary permissions. The platform controls carrier, SIM selection and emergency routing. If denied default Phone role, the keypad falls back to the operating system dialer via `ACTION_DIAL`. Emergency-looking numbers are *never* placed automatically by this app; the system dial UI handles confirmation and routing.

Do **not** use this preview as your sole trusted emergency dialer. Test with provider-approved non-emergency numbers and revert in Android Settings → Default apps → Phone app. Do not place test calls to emergency services.

## Planned, not implemented

The broader product proposal includes call screening, on-device spam defenses, AI screening, text-based calling, translation, automatic transcripts and summaries, recording, intelligent voicemail, business menu assistance, video integrations, personal calling cards, and cross-device features. None of these is claimed to work in this preview. Android role restrictions, carrier capabilities, lawful consent and device/OEM testing remain prerequisites.

## Verification gates before release

1. CI unit tests, lint, APK metadata and manifest/permissions inspection.
2. Disposable Android emulator functional tests: role request, contact-permission denial, in-call service controls and notification behavior.
3. Representative physical devices: dual SIM, carrier calls, DTMF, Bluetooth, locked-screen notifications and OEM battery management.
4. Privacy, accessibility, emergency-call preservation and platform policy review; inspect UI screenshots against GoreeCloud UI.
5. Record verified signing/provenance/checksum; deliver standalone signed APK in authorized `GoreeCloud/Artifacts/` if an APK delivery is requested.

### Architecture

`DialerActivity` displays navigation and operating-system data. `PhoneInCallService` is bound by Android Telecom. `CallSession` manages only in-process live calls and publishes UI updates. `CallActionReceiver` handles explicit app-internal notification actions. `PhoneMatcher` provides offline number/T9 matching. No server, identity account, Mesh synchronization, or Wardveil cloud feed is active.
