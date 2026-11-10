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

**Synthetic Android-provider safety gates (source implementation; CI pending):**
The ContactsRepository now treats a missing/null cursor, missing required column,
upstream exception, canceled request or over-budget directory as a **failed entire
snapshot** rather than publishing partial or falsely empty contacts. A bounded
10,000 aggregate-contact / 60,000 row-per-related-query traversal limit prevents
unbounded in-memory enumeration; legitimate larger address books require a
future paginated design. Empty contacts can still show valid contact groups.
A new entirely synthetic MatrixCursor instrumentation suite checks null/empty,
field joins/deduplication, malformed records, cancellation and row caps.
Those tests never access real contacts, modify system accounts or claim OEM
provider-fault / load-testing acceptance. The actual query can still block in
OEM/provider code; cancellation is cooperative, not guaranteed.

**Quality gates:** No network/write/SMS/call permissions; explicit sharing and export; pure JVM tests; packaged APK/lint; disposable emulator smoke. Real OEM provider, physical device, accessibility, visual acceptance, release and sync remain unverified.
