# GoreeCloud Android Defaults

Native, Android-only GoreeCloud default applications, developed independently in reviewable modules.

## GoreeCloud Dialer

The [Dialer source](dialer/) is a Kotlin Android application intended to request Android's **default Phone app** role on compatible cellular devices. It includes a native keypad, on-device T9 matching, optional contacts and call history, incoming/ongoing call controls, and no Internet permission.

**Development preview only.** The app has not yet been accepted on physical devices or approved as an emergency-calling replacement. Read the [Dialer setup and feature status](dialer/README.md) before changing your default phone app.

## GoreeCloud Contacts — standalone repository

**Authoritative source:** [GoreeCloud/contacts](https://github.com/GoreeCloud/contacts), migrated through [Contacts PR #1](https://github.com/GoreeCloud/contacts/pull/1) with the 33 Contacts-specific commits preserved on its `main` branch. It remains an **Android-only development preview**, not an accepted default app or a deployed CardDAV client. The package identity `com.goreecloud.contacts` and offline-first permission model were retained.

The [previous monorepo Contacts module](contacts/) and [its original documentation](contacts/README.md) are kept temporarily for legacy CI/other module compatibility, source provenance and rollback, **not as the owner of new Contacts development**. Do not remove it until active consumers and native acceptance are verified. The Dialer and Contacts remain independently packaged products; no installed personal contacts or device roles were migrated by the repository change.

Other products, including Clock, may be developed in separate pull requests; their existence in a PR does not mean they are on this branch.
