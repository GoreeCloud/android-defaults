# GoreeCloud Contacts — Android feature register

**Scope: Android only.** Source implementation is not evidence of release, device acceptance or all-platform support.

| Feature | Status | Notes |
| --- | --- | --- |
| Native Android directory | Implemented in source | Permission-gated background Contacts Provider reads |
| Name, number, email and organization search | Implemented in source | Pure JVM matching tests |
| Accent-insensitive and formatted-phone search; local sort/filter | Implemented in source; CI pending | Read-only view settings: name, organization or favorites-first; with-phone/with-email filters; session/restored-screen state only |
| Favorites | Implemented in source | App-private preference, not system STARRED; explicit user-confirmed clear-all control (CI pending) |
| Optional Contacts screen-capture protection | Implemented in draft source; per-head CI required | Explicit confirmation and reversible app-window FLAG_SECURE; disabled by default, note dialogs independently secure |
| Groups | Implemented in source | Read-only Android group membership |
| Add and edit | Implemented in source | User-confirmed Android intents; no direct writes |
| Calls, SMS and email | Implemented in source | Delegate to compatible installed apps |
| Opt-in address-to-maps handoff | Implemented in source; CI pending | Preview exact address, confirm and choose recipient mapping application; no device location access |
| Contact sharing | Implemented in source | Affirmative field selection (none preselected), exact preview, then recipient app chooser; no notes/addresses |
| Single-contact vCard/CSV export from profile | Implemented in source; CI pending | Uses existing size/field validation, confirmation and user-chosen SAF destination |
| Partial vCard export | Implemented in source; export preflight CI pending | Names, phones, emails and organizations via document picker; checks byte/field/contact budgets and control characters before opening destination |
| Favorite/group-scoped exports | Implemented in source; CI pending | Explicit subset selection followed by existing export confirmations and SAF destination; no background transfers |
| Limited CSV export | Implemented in source; per-commit CI required | Name/phone/email/organization, formula-neutralized quoted CSV, full preflight, explicit SAF save picker; not lossless |
| Single vCard import preview | Source and API26/35/36 emulator CI passed in PR #24; OEM editor acceptance open | Explicit SAF file selection; UTF-8 vCard 3/4 single-record strict parse; preview omitted fields and Android user-confirmed editor; no silent writes |
| Duplicate review | Implemented in source | Read-only bounded candidate chooser opens any suggested profile; no direct merge |
| Floating dock and semantic themes | Implemented in source | Rendered device/GC UI review pending |
| Postal addresses, birthdays, anniversaries, custom dates and notes | Implemented in source; note-privacy CI pending | Read-only bounded Android Data queries; notes hidden by default behind explicit secure dialog; excluded from share/export |
| Read-only upcoming birthdays and anniversaries | Implemented in source; CI pending | Next 90 days, local calculations, no calendar writes, reminders or age inference |
| Photos, posters, birthday notifications and rich-field editing | Planned | Android provider/UI and privacy review |
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
OEM/provider code; cancellation is cooperative, not guaranteed. A provider may
report cancellation only as its final cursor is exhausted or closed: the
repository now rechecks cancellation at both boundaries and refuses a
completed-looking snapshot. Synthetic instrumentation also rejects duplicated,
zero or negative provider contact IDs rather than silently overwriting one
record with another. Disposable API26/35/36 instrumentation now also constructs
5,000- and 10,000-entry synthetic-only Contacts Provider cursors, verifies no
rows are silently lost or duplicated, logs *only* row counts and monotonic
traversal time, and rejects 10,001 entries at the documented 10,000-contact
limit. These tests are a reproducible provider-memory/integrity baseline,
**not** a recycled UI-row benchmark, strict latency SLA, a full populated-device
stress test or a solution to OEM query hangs. They cannot force an uncooperative
OEM provider to finish or prove performance on physical devices.

**Export safety:** The selected destination is opened only after a full
read-only in-memory preflight of every vCard. Pending export snapshots are now
cleared when Contacts access is revoked, and a successful document-picker
result does not authorize an export after READ_CONTACTS permission is lost.
Permission is rechecked on picker return and by the queued worker immediately
before opening the user-chosen destination. These checks cannot retroactively
revoke a write that has already begun or make external providers atomic.
Limits are 10,000 contacts,
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
write Contacts Provider records itself. The limited FN/TEL/EMAIL/ORG
preview rejects bidi formatting/isolate controls, Unicode line/paragraph
separators and malformed UTF-16 rather than copying visually deceptive or
lossy fields into the prefilled editor; ordinary international text, escaped
vCard newlines and valid supplementary Unicode remain supported. This
conservative policy may reject otherwise displayable vCards containing bidi
formatting marks; it does **not** silently rewrite source fields or claim full
import, RFC-complete fidelity or safe import of external attachments/photos.

**Limited CSV export:** User-selected `text/csv` destination and explicit
confirmation. Values are quoted for CSV, leading formula-like payloads are
prefixed with a literal apostrophe, and all rows are preflighted against
field, count and byte limits before the document is opened. CSV
preflight also rejects lone/misordered UTF-16 surrogate code units from
provider data rather than silently replacing approved characters during UTF-8
encoding; valid paired supplementary Unicode is preserved. This applies to
all four exported columns including joined phone/email values. Multiple
phone/email values are joined in one column, so this is deliberately not a
round-trip backup. External document providers can still leave a partial
file if writing fails after opening; no atomicity or encryption is claimed.

**Explicit subset export controls (this PR):** The Tools view can select
favorite contacts (private GoreeCloud favorite keys) or an existing Android
group, then export only those matched contact records. Group selection is
read-only and requires choosing a format, reviewing the export confirmation
and selecting the destination provider. Empty selections are refused. Neither
workflow expands vCard/CSV field fidelity, gains group-write permissions nor
changes server synchronization or recovery behavior.

**CI screenshot readiness hardening (under verification):** The isolated
API 26/35/36 emulator capture helper now wakes the disposable emulator,
requests keyguard dismissal without clicking dialogs or granting Contacts,
waits for explicit ActivityManager launch completion, and makes a bounded
20-attempt UI hierarchy check. On failure it captures a diagnostic image and
limited foreground-process information, but does not dump personal contacts.
A captured Android 35 failure screenshot from run 42736882461 confirmed a
system **Pixel Launcher isn't responding** overlay on top of a correctly
rendered GoreeCloud Contacts permission screen. For precisely this system-app
ANR, the CI-only script now attempts one bounded force-stop/relaunch of Pixel
Launcher before continuing. This is a diagnostic/recovery measure, **not
proof** that all Android 35 startup failures are fixed. See issue #26; reruns,
visual inspection and OEM acceptance remain open.

**Read-only extended details (this PR):** Android Contacts Provider Data
now supplies available formatted postal addresses, significant dates
(birthday, anniversary and labeled custom dates), and notes to the local
contact detail and offline search views. Per-contact row/value caps prevent
unbounded data retention, and missing/malformed related cursors fail the
entire snapshot rather than silently dropping fields. These private details
are deliberately **excluded** from the existing text share, CSV export
and vCard export. No new permissions, trackers, external lookup, write
operations, birthday notifications or address-map transfers are introduced.
This is not full-field export or recovery. Synthetic emulator tests and
a real-OEM accessibility review remain mandatory.

**Directory controls (this PR):** Offline searches normalize combining accent
marks and allow phone-digit searches across punctuation and spaces. Sort by
name, organization (unassigned last) or app-local favorites; filter to
contacts with a phone or email. Favorite/group boundaries remain intact.
Controls live only in the Activity saved state (no server, new permission or
background transfer). Pure JVM tests cover Unicode names, multiple field
terms, digit search, ordering and subset isolation. OEM accessibility and
large-directory performance acceptance remain open.

**UTF-8-safe vCard physical line folding (CI under review):** All generated vCard
3.0 properties are folded to at most 75 **UTF-8 octets per physical line**,
including the RFC continuation whitespace. Multi-byte Unicode code points
are kept intact, malformed lone surrogates fail validation, and the existing
byte/field/contact export budgets remain mandatory before opening the
selected destination. Pure JVM regression tests cover accented names,
emoji/other multibyte characters, and round-trip through the application's
limited vCard importer. This improves interoperability but does not provide
full field or multi-contact import fidelity.

**Conservative duplicate review (this PR):** Candidate clusters are built
from case-insensitive email matches and canonical telephone digits. An
explicit +1 NANP number may match its ten-digit national form; different
international prefixes are no longer collapsed merely because their final
ten digits agree. Short numbers and phone strings with extension markers are
ignored to reduce misleading matches. This intentionally favors false
negatives over false-positive recommendations. No automatic merges, deletion
or Contacts Provider writes are introduced. CI and real-world cross-region
format validation remain open.

**Selective sharing and one-person exports (this PR):** From a contact
profile, the user can select individual name, telephone or email fields to
share. **Nothing is preselected**; only the affirmative selections appear in
an exact second-stage preview before the system recipient chooser. The
share payload removes control-character line breaks and refuses malformed,
oversized or Unicode bidi-formatting-controlled **selected** values before the
second-stage share preview. Valid international text and emoji remain intact;
an unsafe but *unselected* field cannot block or leak into another selected
field. Organization, private notes, addresses, dates, account
information and other unselected details are excluded. The profile also
offers vCard or CSV export of **that single contact only**, reusing the
existing explicit document-location picker, count/size preflight and limited
field formats. No background transfer, new permission, provider write or
implicit remote destination is introduced. Real receiver-app tests and
accessibility remain independent acceptance gates.

**Optional map handoff (this PR):** When an Android contact contains a
formatted postal address, its profile offers `Open address in maps`. The
user sees that address in a consent dialog before Android opens the receiving
app chooser. A pure Kotlin builder percent-encodes the address into a `geo:`
query, refuses invalid/oversized fields and prevents URL query injection.
GoreeCloud Contacts uses no GPS/location or Internet permission and makes no
network request. The chosen mapping application may use the Internet and
receives the address **only after the user's explicit action**. No background
geocoding, telemetry or stored coordinates are introduced. External app
availability and physical-device handoff acceptance remain unverified.
The initial isolated implementation passed JVM tests, APK build, Lint and
Android 26/35/36 emulator CI. Verification of the branch after rebasing onto
the merged selective-sharing change is tracked independently in this PR.

**Local favorites privacy control (this PR):** Tools shows a count of
app-private favorite markers and a separately confirmed `Clear app-local
favorites` action. The action removes only the `keys` preference entry; it
does not delete contact records, write Android STARRED, modify accounts, or
remove real phone numbers. An instrumentation test uses a disposable
synthetic preference file to verify toggling, key isolation, explicit erasure
and cleanup without reading a personal Contacts Provider. Android backup
exclusions remain unchanged. The action is intentionally irreversible for
local markers, though contacts may be favorited again.

**Private notes reveal-on-demand (this PR):** Contact notes never appear
inline on the default contact profile. Instead the profile shows only a
count and a `Reveal private notes` action. The user must open a transient
Android dialog to view the notes and can close it with `Hide notes`.
No reveal flag, note text or note-history is persisted in preferences, and
notes remain excluded from external share, vCard/CSV and map handoff. Pure
JVM tests verify redacted labels and bounded explicit text disclosure.
The revealed dialog also sets Android FLAG_SECURE to discourage screenshots and recent-app previews while open; it is not a guarantee against external cameras or all capture methods.
Android window captures while the dialog is open and real device acceptance
remain separate release considerations.

**Read-only upcoming dates (this PR):** Tools displays up to twelve
birthdays and anniversaries occurring within the next 90 days. Computation
runs locally from the existing in-memory Android Contacts Provider snapshot.
The pure parser accepts full `YYYY-MM-DD` and yearless `--MM-DD` Android
values, ignores malformed and unclassified custom dates rather than inventing
recurrence, and keeps Feb 29 on actual leap years. Sorting and dates use the
device's local calendar day, do not calculate ages, and never create
notifications, calendar events, alerts, analytics or server requests.
JVM regressions cover leap days, malformed dates, date boundaries, result
limits and deterministic ordering. The upcoming-date implementation passed
its initial branch CI; the rebased-head CI still requires verification before
merge. Real provider/OEM date formatting and screen-reader review remain
release gates.

**Per-field provider safety budgets (this PR):** The read-only Android
adapter enforces a 4,096-character maximum on display names, lookup keys,
phone numbers, emails, organization names and group titles, 100 unique
phones or emails per aggregated contact, and 256 distinct group memberships
per contact. Existing per-query 60,000-row budgets and extended-data caps
remain. Exceeding a budget discards the **entire** snapshot rather than
silently truncating or publishing incomplete contacts. Synthetic MatrixCursor
tests cover oversized identity, phones, emails, organization and group names;
phone/email/group limits and an exact-limit positive case. Limits protect
reliability but may reject exotic OEM address books; pagination and streaming
are still needed for production-scale coverage.

**Review each duplicate candidate (this PR):** Selecting a potential
duplicate cluster opens a non-destructive dialog showing up to twenty
contact names, with no automatic choice of the first person. The user can
open any listed candidate's existing profile. The dialog intentionally
omits phone numbers, emails, organization, notes and other private fields;
names are sanitized for control characters, and large clusters are
explicitly capped. Pure JVM tests verify candidate identity, ordering,
privacy omissions and limits. No Contacts Provider mutation, deletion,
merging, telemetry, new permission or network operation is introduced.

**Bounded upcoming-date selection (this PR):** The next-90-days overview
now keeps at most the requested 1–50 candidates in a worst-first heap rather
than materializing and sorting every matching event. It returns the same
ascending date/name/contact-ID ordering while reducing selection memory to
O(maxResults), independent of total contact count. Pure JVM regressions
cover a synthetic 5,000-contact directory, top-one selection and date order.
Provider reads, date inference, permissions, reminders and UI behavior are
unchanged; OEM performance benchmarks and real populated-device reviews
remain required.

**Optional GoreeShield window protection (draft integration):** The Tools view
allows users to opt in to Android `FLAG_SECURE` with an explicit confirmation.
The setting defaults OFF to preserve ordinary screenshots, screen sharing and
accessible workflows unless a user turns it ON. Android blocks many app-window
captures and recent-app previews while enabled, but cannot stop cameras or
all OEM capture paths. Disabling after confirmation removes protection from
normal windows; a separately protected private-note reveal dialog continues
to use its own `FLAG_SECURE`. Only an app-private Boolean is persisted; Android
backup remains disabled. Disposable synthetic instrumentation tests cover
setting defaults, opt in/out, and removal without contact-provider access.
Physical screenshot, casting and TalkBack behavior are separate release gates.
