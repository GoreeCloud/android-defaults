# GoreeCloud Android Defaults

Native Android monorepo for GoreeCloud's simple default apps.

## Apps

| Module | Status | Purpose |
| --- | --- | --- |
| [Clock](clock/) | v0.1.0 development preview | Alarms, timer, stopwatch, settings |

The Clock module uses Kotlin, Jetpack Compose, and the canonical GoreeCloud UI design token values. Its floating bottom dock contains **Alarms**, **Timer**, **Stopwatch**, and **Settings**, in that order. See [Clock notes](docs/clock.md) for verified scope, permissions, known limits and build instructions.

### Build

Requires JDK 17, Android SDK 35 and Gradle 9.5.0 (the CI workflow installs Gradle; this repository intentionally has no wrapper binary yet).

Run:

    gradle :clock:testDebugUnitTest :clock:assembleDebug :clock:lintDebug

The produced APK is a development build, **not** a verified release or device-tested installer. Android APKs offered for founder installation must be separately verified and delivered to the designated GoreeCloud/Artifacts Drive directory.

## Design & privacy

GoreeCloud UI is the ecosystem's design authority. Clock stores alarms, stopwatch and countdown state locally in app-private SharedPreferences. No account, telemetry, advertisements, network permission, or cloud sync is included.

## License

No code license has been selected for this newly added module. Public visibility alone does not grant reuse rights.
