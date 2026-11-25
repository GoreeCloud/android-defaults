# GoreeCloud Android Defaults

Native, Android-only GoreeCloud default applications, developed independently in reviewable modules.

## GoreeCloud Dialer

The [Dialer source](dialer/) is a Kotlin Android application intended to request Android's **default Phone app** role on compatible cellular devices. It includes a native keypad, on-device T9 matching, optional contacts and call history, incoming/ongoing call controls, and no Internet permission.

**Development preview only.** The app has not yet been accepted on physical devices or approved as an emergency-calling replacement. Read the [Dialer setup and feature status](dialer/README.md) before changing your default phone app.

## GoreeCloud Contacts — historical standalone-source handoff

**Source retirement (November 18, 2026):** The former `GoreeCloud/contacts` GitHub repository was deleted by the founder as part of a 45-repository removal. The earlier Contacts migration and pull request are **historical records, not accessible current source links**. No new standalone Contacts source repository is currently designated. The old Android-only development preview was never an accepted default app or deployed CardDAV client; deletion does not change any installed application or on-device contact data.

The [legacy monorepo Contacts module](contacts/) and [its original documentation](contacts/README.md) remain in this repository for CI compatibility, source provenance and possible separately reviewed recovery; **they are not an independently validated or maintained replacement**. Do not remove them without verifying active consumers, ownership and source/data preservation. Dialer and Contacts remain separately packaged concepts; no installed personal contacts or device roles were migrated by the earlier repository split.

Other products, including Clock, may be developed in separate pull requests; their existence in a PR does not mean they are on this branch.

## License and source boundaries

The current GoreeCloud-authored source, tests and documentation on this default branch are available under the [MIT License](LICENSE), copyright (c) 2026 GoreeCloud, subject to applicable component rights. The legacy Contacts mirror retains its source history and attribution. The former standalone Contacts repository and its separate license are no longer accessible on GitHub; do not assume this root license automatically governs any separately recovered source.

Unmerged development branches, including the proposed **GoreeCloud Clock**, are not automatically cleared for redistribution merely because this repository has a root MIT license. Review each module's ownership, contributions, third-party code and applicable notices before merging or distributing it. Android SDK/Gradle dependencies and external assets retain their independent terms. This license does not establish production/default-phone or emergency-calling acceptance.

### Dialer Compose transition

A reviewable, read-only privacy panel is being migrated to the canonical [GC UI Compose source](dialer/gc-ui-compose/UPSTREAM.md), while the safety-critical Phone-role and call-control UI remains Android Views. This is **partial** platform adoption, not yet primary-framework compliance or a qualified replacement dialer. Follow [Dialer issue #84](https://github.com/GoreeCloud/android-defaults/issues/84) for the complete migration and acceptance gates. No Contacts or Clock source changes accompany this slice.
