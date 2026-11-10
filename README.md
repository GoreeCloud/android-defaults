# GoreeCloud Android Defaults

Native, Android-only GoreeCloud default applications, developed independently in reviewable modules.

## GoreeCloud Dialer

The [Dialer source](dialer/) is a Kotlin Android application intended to request Android's **default Phone app** role on compatible cellular devices. It includes a native keypad, on-device T9 matching, optional contacts and call history, incoming/ongoing call controls, and no Internet permission.

**Development preview only.** The app has not yet been accepted on physical devices or approved as an emergency-calling replacement. Read the [Dialer setup and feature status](dialer/README.md) before changing your default phone app.

## GoreeCloud Contacts

The [Contacts source](contacts/) is a separate native Kotlin Android development preview with opt-in on-device contact reading, search, app-local favorites, native group browsing, contact details, user-confirmed Android add/edit, calling/messaging shortcuts, explicit sharing and partial vCard export, and duplicate suggestions. It declares **no Internet or WRITE_CONTACTS permission**.

See [Contacts setup, limitations and feature register](contacts/README.md). The Dialer and Contacts remain independent applications with distinct package identities.

## GoreeCloud Clock (draft integration candidate)

The [Clock module](clock/) is staged on **this integration branch only**, alongside the unmodified Dialer and Contacts modules. It is a Kotlin/Jetpack Compose Android development preview with **Alarms**, **Timer**, **Stopwatch**, and **Settings**. It has no Internet permission and stores its settings locally. Review the [Clock feature and privacy notes](docs/clock.md) before installing or relying on it.

For disposable development CI, run `gradle :clock:testDebugUnitTest :clock:assembleDebug :clock:lintDebug` from the repository root using JDK 17 and the workflow-provisioned Gradle 9.5/Android SDK 35. The root Clock Gradle project is separate from the existing `contacts/` and `dialer/` Gradle builds; their existing checks remain intact.

**Merge and release gates remain open:** the original [Clock PR #1](https://github.com/GoreeCloud/android-defaults/pull/1) was developed separately and does not represent merged `main`. A working combined tree and green hosted emulator tests do **not** verify physical alarm audibility, Android background behavior, accessibility, safe upgrade, or stable APK delivery. **License reconciliation is also required**: the current repository has a MIT `LICENSE`, but the original Clock branch described its source license as undecided. Do not infer resolution or authorize a main-branch release from this staging work.
