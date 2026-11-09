# GoreeCloud Contacts — Android feature register

**Scope: Android only.** Source implementation is not evidence of release, device acceptance or all-platform support.

| Feature | Status | Notes |
| --- | --- | --- |
| Native Android directory | Implemented in source | Permission-gated background Contacts Provider reads |
| Name, number, email and organization search | Implemented in source | Pure JVM matching tests |
| Favorites | Implemented in source | App-private preference, not system STARRED |
| Groups | Implemented in source | Read-only Android group membership |
| Add and edit | Implemented in source | User-confirmed Android intents; no direct writes |
| Calls, SMS and email | Implemented in source | Delegate to compatible installed apps |
| Contact sharing | Implemented in source | Text preview and user-selected recipient |
| Partial vCard export | Implemented in source | Names, phones, emails, organization via document picker |
| Duplicate review | Implemented in source | Suggestions only; no direct merge |
| Floating dock and semantic themes | Implemented in source | Rendered device/GC UI review pending |
| Rich data fields, photos, posters, birthdays | Planned | Android provider/UI support and privacy review |
| CSV/vCard import, full export, bulk edit | Planned | Fidelity, permissions, rollback |
| Delete, Trash, history, backups and restore | Planned | Data safety and retention design |
| Group writes, ringtones, SIM moves | Planned | New Android manage-contacts permissions review |
| Widgets, QR exchange | Planned | Platform API and privacy design |
| CardDAV, GoreeCloud Mesh sync, accounts | Planned | Backend/Identity/Wardveil/GoreeShield verification |
| Default role, production deployment, device acceptance | Not established | Separate release gates |

GoreeShield governs privacy; Wardveil cybersecurity; GoreeCloud Mesh integrations; GoreeCloud Identity access control; GoreeCloud UI interface design.

**Quality gates:** No network/write/SMS/call permissions; explicit sharing and export; pure JVM tests; packaged APK/lint; disposable emulator smoke. Real OEM provider, physical device, accessibility, visual acceptance, release and sync remain unverified.
