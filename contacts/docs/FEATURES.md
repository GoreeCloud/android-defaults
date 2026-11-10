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
| Partial vCard export | Implemented in source; export preflight CI pending | Names, phones, emails and organizations via document picker; checks byte/field/contact budgets and control characters before opening destination |
| Limited CSV export | Implemented in source; per-commit CI required | Name/phone/email/organization, formula-neutralized quoted CSV, full preflight, explicit SAF save picker; not lossless |
| Single vCard import preview | Source and API26/35/36 emulator CI passed in PR #24; OEM editor acceptance open | Explicit SAF file selection; UTF-8 vCard 3/4 single-record strict parse; preview omitted fields and Android user-confirmed editor; no silent writes |
| Duplicate review | Implemented in source | Suggestions only; no direct merge |
| Floating dock and semantic themes | Implemented in source | Rendered device/GC UI review pending |
| Rich data fields, photos, posters, birthdays | Planned | Android provider/UI support and privacy review |
| Multi-card vCard/CSV import, full export, bulk edit | Planned | Fidelity, permissions, rollback; single-card preview is not full import |
| Delete, Trash, history, backups and restore | Planned | Data safety and retention design |
| Group writes, ringtones, SIM moves | Planned | New Android manage-contacts permissions review |
| Widgets, QR exchange | Planned | Platform API and privacy design |
| CardDAV, GoreeCloud Mesh sync, accounts | Planned | Backend/Identity/Wardveil/GoreeShield verification |
| Default role, production deployment, device acceptance | Not established | Separate release gates |

GoreeShield governs privacy; Wardveil cybersecurity; GoreeCloud Mesh integrations; GoreeCloud Identity access control; GoreeCloud UI interface design.

**Synthetic Android-provider safety gates (CI-backed source, OEM acceptance open):**
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

**Export safety:** The selected destination is opened only after a full
read-only in-memory preflight of every vCard. Limits are 10,000 contacts,
10 MiB total, 128 KiB per card, 4,096 characters per field and 100 fields
per phone/email kind. Oversized/invalid inputs fail without deliberate
truncation. This preview does not guarantee atomic writes when an external
document provider or storage fails **after** it opens the file; inspect and
delete any partial destination manually. It does not preserve fields absent
from the limited vCard representation. Signing and restore fidelity remain open.

**Cross-version visual gate (development CI):** An isolated Android API 26/35/36
emulator matrix now exercises the installed Contacts package with instrumentation
and captures its **permission-denied empty state** under default and 1.5× font scale
plus system dark mode where available (API 35/36). The capture script explicitly
refuses non-emulators, clears only the disposable emulator's Contacts app, and
does not grant permissions or read real contacts. UI hierarchy visibility is
checked before each screenshot; diagnostics on failure are not acceptance.
Screenshot review, TalkBack, dynamic type beyond 1.5×, RTL, tablet/landscape,
and genuine OEM use remain independent quality gates. The API 26/35/36 workflow matrix runs on disposable emulators; a green run does not constitute visual or physical-device acceptance.

**Quality gates:** No network/write/SMS/call permissions; explicit sharing and export; pure JVM tests; packaged APK/lint; disposable emulator smoke. Real OEM provider, physical device, accessibility, visual acceptance, release and sync remain unverified.

**Limited vCard import path (CI-tested in PR #24; OEM editor validation open):** File selection uses the
system document picker. An application-worker bounded reader accepts at most
128 KiB of strict UTF-8 (vCard 3.0 or 4.0) and **exactly one card**. A pure
parser rejects malformed or unknown-encoding supported fields, excessively long
lines, oversized fields, multi-card documents and excessive phone/email values.
The preview clearly lists supported fields and counts omitted properties.
Only after affirmative approval is a prefilled Android contact-insert editor
opened; the user still chooses the account and taps Save. The app does not
write Contacts Provider records itself. This does **not** claim full import,
RFC-complete fidelity or safe import of external attachments/photos.

**Limited CSV export:** User-selected `text/csv` destination and explicit
confirmation. Values are quoted for CSV, leading formula-like payloads are
prefixed with a literal apostrophe, and all rows are preflighted against
field, count and byte limits before the document is opened. Multiple
phone/email values are joined in one column, so this is deliberately not a
round-trip backup. External document providers can still leave a partial
file if writing fails after opening; no atomicity or encryption is claimed.
