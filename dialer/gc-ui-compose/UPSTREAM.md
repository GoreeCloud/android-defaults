# Canonical GoreeCloud UI source snapshot — Android Dialer

Source: https://github.com/GoreeCloud/gc-ui
Pinned upstream commit: `8b06bfb04aef5974fc3b01fb69f6af343aa24cee`
Upstream module path: `android/material3-expressive/gc-ui-compose/`
License: MIT; original `LICENSE` copied unmodified.

The `build.gradle.kts`, `AndroidManifest.xml`, theme, palette, floating
navigation and palette test sources are copied **without edits**. The
`UPSTREAM.md` file is consumer-added provenance. Depend on this module locally;
do not import runtime source from the network at build time. Independent
AndroidX / Google Maven dependencies retain their upstream licenses.

The Dialer remains a privacy-sensitive calling app. This UI adapter has no
access to Phone roles, provider cursors, call audio, notification actions,
call content or network endpoints. Changes require reviewed source diff,
CI build, role/calling security tests, emulator UI checks and real-device
acceptance before a production default-Phone release.
