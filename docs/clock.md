# Clock — Android development preview

## User experience

- Floating bottom navigation: **Alarms**, **Timer**, **Stopwatch**, **Settings**, with selected-state indicators and accessible labels.
- Alarms: add a local alarm, enable/disable it, select repeat weekdays and delete it. Scheduling uses Android AlarmManager.setAlarmClock and reschedules following reboot, time/time-zone adjustment and app replacement.
- Timer: set a countdown, start, pause, resume and reset. An AlarmManager alarm notifies the user on completion even when the interface is not foregrounded.
- Stopwatch: start, pause, reset and capture laps, with elapsed time maintained against Android elapsedRealtime.
- Settings: system/light/dark appearance; notification and exact-alarm access state and shortcuts to system controls.

## Platform behavior

The app is a Kotlin/Jetpack Compose Android module for API 26–35. Alarms and background timers require both notification permission (Android 13+) and exact-alarm access (Android 12+), which users explicitly grant in Android settings. If missing, the app refuses to mark a newly enabled alarm or timer as scheduled.

Notifications use a high-importance alarm-sound channel. Android notification controls, Do Not Disturb, device power policies and OEM behavior can affect alert delivery and sound. This is an **initial local Clock implementation**, not an independently device-tested safety-critical alarm substitute. No fullscreen alarm ringing, custom ringtones, persistent lap history, or cloud synchronization is implemented.

The app does not request INTERNET; stored times are local to the device and are not backed up. A stopped/rebooted stopwatch is restored conservatively and its elapsedRealtime session is invalidated when the underlying monotonic clock resets.

## GoreeCloud UI

The Compose light/dark colors are mapped from the versioned GoreeCloud UI token reference in GoreeCloud/gc-ui, tokens/gc.v1.tokens.json; product acceptance still requires rendered phone/tablet screenshots and accessibility review. The bottom dock uses a purposeful Lens-inspired raised surface with a solid fallback. The adaptive launcher icon uses approved GC UI semantic colors and a custom vector clock dial; actual launcher rendering remains subject to device review. System status/navigation icon contrast follows the selected appearance.

## Engineering validation

CI is configured to run :clock:testDebugUnitTest, :clock:assembleDebug and :clock:lintDebug. **Actual results must be recorded after the workflow runs**; the presence of this workflow is not proof of passing validation. Device behavior, exact-alarm permissions, reboot broadcasts, notifications, accessibility and battery policy interactions still require physical testing.
