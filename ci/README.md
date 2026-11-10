# Android Defaults source admission (development stage)

The `admission.yml` GitHub Actions workflow runs on **every pull request** and
each push to `main`, including documentation-only changes. It has read-only
repository permissions, uses an immutable reviewed checkout action with no
persisted credentials, and executes only dependency-free source policy and
synthetic device-admission checks.

`python3 ci/check_merge_admission.py` validates that:

- the Contacts and Dialer workflows remain present with their established
  source-specific triggers;
- the admission workflow has **no path filters**, so a docs-only change still
  receives a predictable hosted check;
- all workflow actions are exact approved immutable commits and every checkout
  disables credential persistence;
- workflow permissions stay `contents: read`.

`python3 -m unittest discover -s ci/tests -p 'test_*.py'` exercises both
the valid policy and deliberate negative inputs (missing triggers, scope
escalation, path filters and unpinned actions). Existing mock-only emulator
target-denial checks, fresh Contacts UI hierarchy tests and Dialer QA source
tests run without touching a physical device.

## What this does **not** establish

This workflow is **not a protected-branch rule**, a PR approval, an Android
APK build, a Phone-role safety test or a signature/release authorization.
The Contacts and Dialer build/emulator workflows still use `paths` filters.
**Do not make those filtered statuses mandatory** in GitHub branch rules: an
unrelated change can leave them skipped or pending. An actual required-status
policy must incorporate trustworthy path-based evaluation of the appropriate
source-specific checks and be validated on positive/negative PRs, including
forks, canceled runs and bypass recovery.

GitHub issue #50 tracks that owner-approved branch/ruleset decision.
Draft Clock integration and the source-license decision (issue #46), device
acceptance, carrier/emergency behavior and Stable release remain separate
gates. A green preflight status does not bypass any of them.
