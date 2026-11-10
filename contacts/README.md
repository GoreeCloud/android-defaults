# GoreeCloud Contacts — Android

A separate native Kotlin Android application in the GoreeCloud Android Defaults monorepo.

**Maturity: 0.1.0 development preview, not a certified replacement for the device's contacts application.** Android-only, independent from the Dialer. Minimum API 26; compile and target SDK 35.

## Current source implementation

- On-device Android Contacts Provider read with explicit READ_CONTACTS runtime consent. No contact data upload.
- Search names, numbers, emails and organizations; alphabetized directory; app-local favorites; Android groups and membership filtering.
- Profile cards with phone/email/organization and locally read formatted addresses, important dates and notes; add/edit through Android system contact intents. Private extended details remain absent from the current share and export paths.
- Call via ACTION_DIAL, compose SMS/email via installed apps, share only after preview and explicit confirmation.
- Read-only duplicate suggestions by normalized phone/email.
- User-initiated vCard export to a Storage Access Framework destination, covering name, phone, email and organization.
- **Scoped export:** choose favorite contacts or an existing Android group and export only those records as vCard or CSV after explicit confirmation.
- User-initiated **CSV export** for limited contact fields, preflighted for field/size limits and spreadsheet-formula injection protection before opening a user-selected destination.
- **Single-contact vCard import preview** for strict UTF-8 vCard 3/4 (up to 128 KiB): choose a file, review supported data and omissions, then open Android's user-confirmed insert editor. No direct provider inserts or automatic bulk import.
- Offline-only design with no INTERNET, WRITE_CONTACTS, call or SMS permission; no telemetry or server.
- Floating bottom navigation, responsive native UI and semantic light/dark surfaces.

**Not implemented:** Android system STARRED updates, direct bulk contact changes, group edits, delete/Trash, rich field round-trips, birthday notifications, contact photos, QR exchange, SIM transfers, multi-contact/full-fidelity imports, self-hosted sync, backups, default-handler replacement, device/release acceptance.

## Build

Uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.21, Android SDK 35.

From this directory:

    gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

Debug output: contacts/app/build/outputs/apk/debug/app-debug.apk.

The debug APK is not a signed release or device-accepted installation. CI checks build, unit tests, lint, APK permissions and disposable emulator launch.

## Privacy, Android compatibility and remaining gates

READ_CONTACTS must be granted before listing contacts. Choosing a vCard file and opening Android's contact editor does not require full-directory access. Favorite lookup keys are private and excluded from Android backup/transfer. Exports (vCard or CSV) are made only after choosing a document destination; shares only after confirmation and recipient selection. Once exported or shared, recipient storage and policies apply. There is no hidden sync.

Android's October 2026 guidance favors user-confirmed system contact intents instead of future-restricted WRITE_CONTACTS:
https://developer.android.com/blog/posts/reliable-way-to-add-and-edit-contacts-android

CSV export caveats: multiple phone/email values are joined in one column (not fully round-trip safe); formula-like cells may receive a leading apostrophe to avoid spreadsheet formula interpretation. This is not a complete contact backup.

vCard import caveats: only FN, TEL, EMAIL, ORG map into the Android insert intent; additional properties (including photos, structured addresses, birthdays and relationships) are listed as omitted and will **not** be imported. Multi-card documents, non-UTF-8 encodings and large or malformed files are rejected. The user must review and save in their Android contact editor; even provided fields may not be retained by all account-specific editors. No import is committed if they cancel.

Remaining gates: OEM provider behavior (revocation, cancellation, unavailable rows, large collections), full vCard interoperability, synthetic stress tests, API 26/35/36 physical or emulator acceptance, TalkBack, large text, RTL, tablets, night mode and GC UI screenshot audit. Network sync requires separately verified Identity, GoreeShield, Wardveil and Mesh contracts.

See [feature status](docs/FEATURES.md).
