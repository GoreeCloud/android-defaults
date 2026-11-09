# GoreeCloud Dialer — Android development preview

An independently buildable **native Kotlin Android** phone app in the `GoreeCloud/android-defaults` repository. This is a functional source implementation proposal, **not a certified default-phone replacement**, signed release, or validated physical-device deployment.

## Scope implemented in source

- Android `ACTION_DIAL` handler, keypad with long-press +, backspace/long-press clear, strict number validation, Telecom calling, and request to become default Phone app through `ROLE_DIALER`.
- `InCallService` integration, Android 12+ native `CallStyle` notifications (legacy notifications on Android 8–11), call-specific notification action tokens, Android Telecom foreground requests, answer/reject/end, mute, supported audio-output routes, hold/resume, and DTMF.
- Optional, on-device contacts and T9/number search, contact-provider creation hand-off, contact calling/SMS, and local recent-call number search with All/Missed/Incoming/Outgoing/Declined filters (last 150 accessible records).
- Telecom/CallLog presentation flags are respected for private/withheld caller IDs. No caller number or contact name is included in call notifications.
- Light/dark app-local GoreeCloud UI semantic mappings, touch-friendly native controls, labels, and in-call state handling.
- GoreeShield: no Internet or trackers, no recording, no background contact uploads, opt-in runtime access to contacts/call history, cloud/transfer backup exclusion.
- Wardveil: signature/system-bound `BIND_INCALL_SERVICE`, non-exported notification receiver, input normalization, and no direct user-data network egress.

## Build

Requires JDK 17, installed Android SDK 35, and Gradle 8.13:

```bash
cd dialer
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The debug APK is `dialer/app/build/outputs/apk/debug/app-debug.apk`. It is a development build, **not** a standalone APK delivered through GoreeCloud/Artifacts or a phone-tested release. The repository's GitHub Actions workflow validates source policy, JVM tests, assembly, lint and packaged APK privacy policy. Separate disposable Android 26 and 35 emulator jobs exercise installed UI navigation, dial intents and requested permissions; these are not carrier-call acceptance. CI archives are not standalone signed-delivery confirmation.



### Incremental development features

- **Favorites**: Mark or unmark contacts from the Contacts screen. Only numeric
  Android contact IDs are retained in app-private `SharedPreferences`; no copied
  phone numbers or names are stored for Favorites. You can clear the selection
  in Settings, and revoking Contacts permission prevents reading contact details.
- **Audio routes**: Android 14+ uses platform-provided `CallEndpoint` choices
  and `requestCallEndpointChange()` with a failure callback. Android 8–13 uses
  supported legacy audio-routing APIs. Bluetooth/earpiece switching remains
  unverified on physical phones.
- **Multiple calls**: when Android Telecom reports support, the in-call UI offers
  Add call, merge conference and swap conference. This does not force unsupported
  carrier features, and those actions still require real dual-call tests.
- **In-call keypad**: rapid DTMF key taps no longer schedule overlapping tone
  stop callbacks; dismissal always stops any in-progress tone.
- **Screenshot evidence**: Android emulator smoke tests may produce local-only
  blank-state screenshots for UI review. These are CI evidence, not user photos,
  phone data, product release files, or proof of visual acceptance.

## Device use and safety

Install on a **non-primary test phone** with a working carrier account. Launch; choose **Make default phone app** in Settings, then grant necessary permissions. The platform controls carrier, SIM selection and emergency routing. If the default Phone role has not been granted, the app prompts you to select it and **does not place a call**. When this app is the default, an explicit tap on **Call** uses `TelecomManager.placeCall()` for ordinary and emergency numbers. Android is responsible for emergency routing to the preinstalled system dialer, as documented by [Android's default-dialer guidance](https://developer.android.com/develop/connectivity/telecom/dialer-app).

Do **not** use this preview as your sole trusted emergency dialer. Test with provider-approved non-emergency numbers and revert in Android Settings → Default apps → Phone app. Do not place test calls to emergency services.

## Planned, not implemented

The broader product proposal includes call screening, on-device spam defenses, AI screening, text-based calling, translation, automatic transcripts and summaries, recording, intelligent voicemail, business menu assistance, video integrations, personal calling cards, and cross-device features. None of these is claimed to work in this preview. Android role restrictions, carrier capabilities, lawful consent and device/OEM testing remain prerequisites.

## Verification gates before release

1. CI unit tests, lint, APK metadata and manifest/permissions inspection.
2. Disposable Android 26/35 emulator smoke tests: keypad/navigation, populated `tel:` intent, permission gating and packaged permission checks. Actual Telecom call lifecycle and notification interactions are not yet exercised.
3. Representative physical devices: dual SIM, carrier calls, DTMF, Bluetooth, locked-screen notifications and OEM battery management.
4. Privacy, accessibility, emergency-call preservation and platform policy review; inspect UI screenshots against GoreeCloud UI.
5. Record verified signing/provenance/checksum; deliver standalone signed APK in authorized `GoreeCloud/Artifacts/` if an APK delivery is requested.

### Architecture

`DialerActivity` displays navigation and operating-system data. `PhoneInCallService` is bound by Android Telecom. `CallSession` manages only in-process live calls and publishes UI updates. `CallActionReceiver` handles explicit app-internal notification actions only for their originating live call, rejecting stale actions. `PhoneMatcher` provides offline number/T9 matching. No server, identity account, Mesh synchronization, or Wardveil cloud feed is active.
