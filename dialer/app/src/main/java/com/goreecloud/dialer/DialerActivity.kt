package com.goreecloud.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView

/**
 * Native Android UI with a persistent floating bottom dock.
 * No personal information is sent outside the device by this application.
 */
class DialerActivity : Activity() {
    private lateinit var root: LinearLayout
    private var screen = "keypad"
    private var enteredNumber = ""
    private var contactFilter = ""
    private var favoritesOnly = false
    private val favorites by lazy { FavoritesStore(applicationContext) }
    private var recentSearch = ""
    private var recentFilter = "All"
    private var openDtmfOnNextRender = false
    // Android providers may be slow. Never run Contacts/CallLog queries during a
    // frame. A single bounded worker serializes access and generations discard
    // callbacks from permission changes, Activity recreation, or stale reads.
    private val providerExecutor = BoundedProviderWorker()
    /** Update only current keypad matches without tearing down a focused editor. */
    private var refreshKeypadSuggestions: (() -> Unit)? = null
    private var contacts: List<Contact>? = null
    private var contactsLoading = false
    private var contactsFailed = false
    private var contactsCancellation: CancellationSignal? = null
    private val contactsGeneration = RequestGeneration()
    private var recentCalls: List<Recent>? = null
    private var recentsLoading = false
    private var recentsFailed = false
    private var recentsCancellation: CancellationSignal? = null
    private val recentsGeneration = RequestGeneration()
    private val refreshCalls: () -> Unit = {
        runOnUiThread {
            if (screen == "call" && CallSession.foreground() == null) screen = "keypad"
            render()
        }
    }

    private data class Contact(val name: String, val number: String, val id: Long)
    private data class Recent(val number: String, val type: Int, val whenMs: Long,
        val seconds: Long, val presentation: Int) {
        fun visibleNumber(): String =
            if (presentation == CallLog.Calls.PRESENTATION_ALLOWED) number else ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Only Activity-owned UI state is restored. Provider contents and
            // Telecom call objects are never serialized or persisted here.
            screen = savedInstanceState.getString(STATE_SCREEN, "keypad")
                .takeIf { it in listOf("keypad", "recents", "contacts", "settings", "call") }
                ?: "keypad"
            enteredNumber = savedInstanceState.getString(STATE_DRAFT, "")
            recentSearch = savedInstanceState.getString(STATE_RECENT_SEARCH, "")
            recentFilter = savedInstanceState.getString(STATE_RECENT_FILTER, "All")
            contactFilter = savedInstanceState.getString(STATE_CONTACT_SEARCH, "")
            favoritesOnly = savedInstanceState.getBoolean(STATE_FAVORITES_ONLY, false)
            if (screen == "call" && CallSession.foreground() == null) screen = "keypad"
            // Incoming Telecom intents still take precedence over a saved tab.
            if (intent?.getBooleanExtra(EXTRA_CALL_SCREEN, false) == true) readIntent(intent)
        } else readIntent(intent)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.canvas))
            setOnApplyWindowInsetsListener { view, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    top = bars.top
                    bottom = bars.bottom
                } else {
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                view.setPadding(0, top, 0, bottom)
                insets
            }
        }
        setContentView(root)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Android-managed Activity state only; not SharedPreferences, backup,
        // analytics or network. Restore partial keypad edits across rotation.
        outState.putString(STATE_SCREEN, screen)
        outState.putString(STATE_DRAFT, enteredNumber)
        outState.putString(STATE_RECENT_SEARCH, recentSearch)
        outState.putString(STATE_RECENT_FILTER, recentFilter)
        outState.putString(STATE_CONTACT_SEARCH, contactFilter)
        outState.putBoolean(STATE_FAVORITES_ONLY, favoritesOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        readIntent(newIntent)
        render()
    }

    private fun readIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CALL_SCREEN, false) == true) {
            screen = "call"
            openDtmfOnNextRender = intent.getBooleanExtra(EXTRA_DTMF, false)
        } else if (intent?.action == Intent.ACTION_DIAL && intent.data?.scheme == "tel") {
            enteredNumber = intent.data?.schemeSpecificPart.orEmpty()
            screen = "keypad"
        }
    }

    override fun onStart() {
        super.onStart()
        CallSession.observe(refreshCalls)
        render()
    }

    override fun onStop() {
        CallSession.stopObserving(refreshCalls)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        invalidateProviderCaches()
        if (::root.isInitialized) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        invalidateProviderCaches()
        render()
    }

    override fun onDestroy() {
        // Invalidate callbacks before stopping work; never post into a dead UI.
        invalidateProviderCaches()
        refreshKeypadSuggestions = null
        providerExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun invalidateProviderCaches() {
        contactsCancellation?.cancel()
        recentsCancellation?.cancel()
        contactsCancellation = null
        recentsCancellation = null
        contactsGeneration.invalidate()
        recentsGeneration.invalidate()
        contacts = null
        recentCalls = null
        contactsLoading = false
        recentsLoading = false
        contactsFailed = false
        recentsFailed = false
    }

    /** Retry only the failed provider; invalidate any queued or stale result. */
    private fun retryContactsRead() {
        contactsCancellation?.cancel()
        contactsCancellation = null
        contactsGeneration.invalidate()
        contacts = null
        contactsLoading = false
        contactsFailed = false
        render()
    }

    private fun retryRecentsRead() {
        recentsCancellation?.cancel()
        recentsCancellation = null
        recentsGeneration.invalidate()
        recentCalls = null
        recentsLoading = false
        recentsFailed = false
        render()
    }

    private fun render() {
        if (!::root.isInitialized) return
        // Only ringing calls are eligible to appear above the lock screen.
        val showIncoming = screen == "call" && CallSession.foreground()?.state == Call.STATE_RINGING
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(showIncoming)
            setTurnScreenOn(showIncoming)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (showIncoming) window.addFlags(flags) else window.clearFlags(flags)
        }
        // Do not retain closures pointing at detached views after navigation.
        refreshKeypadSuggestions = null
        root.removeAllViews()
        val largeText = resources.configuration.fontScale >= 1.3f
        val header = column(if (largeText) 12 else 20).apply {
            setPadding(dp(22), dp(if (largeText) 9 else 20), dp(22),
                dp(if (largeText) 8 else 14))
        }
        header.addView(label("GoreeCloud", 13f, R.color.text_soft, true))
        header.addView(label(if (screen == "call") "On your call" else "Dialer",
            if (largeText) 27f else 32f, R.color.text_main, true))
        header.addView(label("Private by default · Android calling", 13f, R.color.text_soft))
        if (CallSession.foreground() != null && screen != "call") {
            header.addView(action("Return to active call", R.color.surface_tonal, R.color.text_main) {
                screen = "call"; render()
            }.apply { topMargin(14) })
        }
        root.addView(header)

        val scroller = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val body = column(20).apply {
            setPadding(dp(22), dp(2), dp(22), dp(18))
        }
        scroller.addView(body)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        when (screen) {
            "call" -> renderCall(body)
            "recents" -> renderRecents(body)
            "contacts" -> renderContacts(body)
            "settings" -> renderSettings(body)
            else -> renderKeypad(body)
        }
        if (screen == "keypad") addCallBar()
        if (screen != "call") addDock()
        else body.addView(action("Back to keypad", R.color.surface_tonal, R.color.text_main) {
            screen = "keypad"; render()
        }.apply { topMargin(12) })
    }

    private fun renderKeypad(body: LinearLayout) {
        // All heights are logical dp, but text sizes use sp. Increase tile
        // height as Android fontScale grows so screen readers and large-font
        // users are not served a visibly clipped keypad.
        val scale = resources.configuration.fontScale.coerceIn(1f, 3f)
        val inputHeight = dp(maxOf(70, (70f * scale).toInt()))
        val tileHeight = dp(maxOf(76, (76f * scale).toInt()))
        body.addView(label("Call a number", 17f, R.color.text_main, true))
        val numberEdit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            textSize = 26f
            setTextColor(color(R.color.text_main))
            hint = if (resources.configuration.fontScale >= 1.3f) "Phone number" else "Enter phone number"
            contentDescription = "Phone number"
            setText(enteredNumber)
            setSelection(text.length)
            setOnEditorActionListener { _, _, _ -> placeCall(); true }
        }
        val numberRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        numberRow.addView(numberEdit, LinearLayout.LayoutParams(0, inputHeight, 1f))
        val delete = action("⌫", R.color.surface_tonal, R.color.text_main) {
            val cursor = numberEdit.selectionStart
            if (cursor > 0) numberEdit.text.delete(cursor - 1, cursor)
        }.apply {
            textSize = 24f
            contentDescription = "Delete previous digit. Long press to clear number"
            setOnLongClickListener { numberEdit.text.clear(); true }
        }
        numberRow.addView(delete, LinearLayout.LayoutParams(dp(70), inputHeight))
        body.addView(numberRow)
        val suggestions = column(7)
        fun updateMatches() {
            suggestions.removeAllViews()
            if (enteredNumber.filter { it.isDigit() }.length < 2) return
            if (!allowed(Manifest.permission.READ_CONTACTS)) return
            val saved = favorites.all()
            val found = queryContacts()
                .asSequence()
                .filter { PhoneMatcher.matches(enteredNumber, it.name, it.number) }
                .sortedByDescending { it.id.toString() in saved }
                .take(3).toList()
            found.forEach { contact ->
                suggestions.addView(action("${contact.name} · ${contact.number}", R.color.surface_tonal, R.color.text_main) {
                    enteredNumber = contact.number
                    numberEdit.setText(enteredNumber)
                    numberEdit.setSelection(numberEdit.length())
                })
            }
        }
        numberEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                enteredNumber = s?.toString().orEmpty()
                updateMatches()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        body.addView(suggestions)
        refreshKeypadSuggestions = {
            if (screen == "keypad" && suggestions.parent != null) updateMatches()
        }
        updateMatches()
        val keys = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )
        for (row in keys) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (digit, letters) ->
                val key = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(R.color.surface, 20)
                    isClickable = true
                    isFocusable = true
                    minimumHeight = tileHeight
                    setPadding(dp(4), dp(5), dp(4), dp(5))
                    contentDescription = if (digit == "0") "Zero. Long press for plus"
                        else if (letters.isEmpty()) digit else "$digit, $letters"
                }
                key.addView(label(digit, 26f, R.color.text_main, true).apply {
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                if (letters.isNotEmpty()) {
                    key.addView(label(letters, 11f, R.color.text_soft).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                } else if (digit == "0") {
                    key.addView(label("+", 12f, R.color.text_soft).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                }
                key.setOnClickListener {
                    numberEdit.text.insert(numberEdit.selectionStart.coerceAtLeast(0), digit)
                }
                if (digit == "0") {
                    key.setOnLongClickListener {
                        numberEdit.text.insert(numberEdit.selectionStart.coerceAtLeast(0), "+")
                        true
                    }
                }
                val lp = LinearLayout.LayoutParams(0, tileHeight, 1f)
                lp.setMargins(dp(3), dp(3), dp(3), dp(3))
                line.addView(key, lp)
            }
            body.addView(line)
        }
        body.addView(label("Android handles emergency routing through the system Phone app.",
            13f, R.color.text_soft).apply { topMargin(12) })
    }

    private fun renderCall(body: LinearLayout) {
        val call = CallSession.foreground()
        if (call == null) {
            body.addView(label("No call in progress", 20f, R.color.text_main, true))
            body.addView(label("Return to the keypad to place a call.", 15f, R.color.text_soft))
            return
        }
        val ringing = call.state == Call.STATE_RINGING
        // Respect the carrier/Telecom presentation flags, including withheld callers.
        val presentation = call.details.handlePresentation
        val canPresent = presentation == TelecomManager.PRESENTATION_ALLOWED
        val number = if (canPresent) call.details.handle?.schemeSpecificPart.orEmpty() else ""
        val name = if (!canPresent) {
            when (presentation) {
                TelecomManager.PRESENTATION_RESTRICTED -> "Private caller"
                TelecomManager.PRESENTATION_PAYPHONE -> "Payphone"
                else -> "Unknown caller"
            }
        } else {
            call.details.callerDisplayName?.toString()?.takeIf { it.isNotBlank() }
                ?: queryContacts().firstOrNull {
                    it.number.filter(Char::isDigit) == number.filter(Char::isDigit)
                }?.name
                ?: number.ifEmpty { "Unknown caller" }
        }
        val state = when (call.state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_ACTIVE -> "Connected"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_CONNECTING, Call.STATE_DIALING -> "Calling…"
            Call.STATE_DISCONNECTING -> "Ending…"
            else -> "Phone call"
        }
        val pane = column(18).apply { background = rounded(R.color.surface, 26) }
        pane.addView(label(state.uppercase(), 13f, R.color.text_soft, true))
        pane.addView(label(name, 28f, R.color.text_main, true))
        if (number.isNotBlank() && number != name) pane.addView(label(number, 16f, R.color.text_soft))
        body.addView(pane)
        if (ringing) {
            body.addView(action("Answer", R.color.positive, R.color.surface) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            }.apply { topMargin(24) })
            body.addView(action("Decline", R.color.negative, R.color.surface) {
                call.reject(false, null)
            }.apply { topMargin(10) })
            return
        }
        val service = CallSession.service
        val muted = if (Build.VERSION.SDK_INT >= 34) {
            service?.mutedOnModernAndroid ?: false
        } else service?.callAudioState?.isMuted ?: false
        val controlGrid = column(10)
        controlGrid.addView(twoActions(
            (if (muted) "Unmute" else "Mute") to {
                service?.setMuted(!muted); CallSession.changed()
            },
            "Audio output" to { showAudioRoutes(service) }
        ))
        if ((call.details.callCapabilities and Call.Details.CAPABILITY_HOLD) != 0 || call.state == Call.STATE_HOLDING) {
            controlGrid.addView(twoActions(
                (if (call.state == Call.STATE_HOLDING) "Resume" else "Hold") to {
                    if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
                },
                "Keypad (DTMF)" to { showDtmf(call, body) }
            ))
        } else {
            controlGrid.addView(action("In-call keypad (DTMF)", R.color.surface_tonal, R.color.text_main) {
                showDtmf(call, body)
            })
        }
        body.addView(controlGrid.apply { topMargin(18) })
        if (openDtmfOnNextRender && call.state != Call.STATE_RINGING) {
            openDtmfOnNextRender = false
            body.post { if (!isFinishing) showDtmf(call, body) }
        }
        if (call.state == Call.STATE_ACTIVE && service?.canAddCall() == true) {
            body.addView(action("Add call", R.color.surface_tonal, R.color.text_main) {
                enteredNumber = ""
                screen = "keypad"
                render()
            }.apply { topMargin(10) })
        }
        val capabilities = call.details.callCapabilities
        if ((capabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
            body.addView(action("Merge conference", R.color.surface_tonal, R.color.text_main) {
                call.mergeConference()
            }.apply { topMargin(10) })
        }
        if ((capabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0) {
            body.addView(action("Swap conference", R.color.surface_tonal, R.color.text_main) {
                call.swapConference()
            }.apply { topMargin(10) })
        }
        if (CallSession.all().size > 1) {
            val other = CallSession.all().firstOrNull { it !== call && it.state == Call.STATE_HOLDING }
            if (other != null) body.addView(action("Swap calls", R.color.surface_tonal, R.color.text_main) {
                other.unhold()
            }.apply { topMargin(10) })
        }
        body.addView(action("End call", R.color.negative, R.color.surface) {
            call.disconnect()
        }.apply { topMargin(22) })
    }

    private fun showAudioRoutes(service: PhoneInCallService?) {
        if (service == null) {
            showError("Audio controls are unavailable for this call.")
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            showModernAudioRoutes(service)
            return
        }
        val audio = service.callAudioState ?: run {
            showError("Audio device information is unavailable.")
            return
        }
        val routes = listOf(
            CallAudioState.ROUTE_EARPIECE to "Earpiece",
            CallAudioState.ROUTE_SPEAKER to "Speaker",
            CallAudioState.ROUTE_WIRED_HEADSET to "Wired headset",
            CallAudioState.ROUTE_BLUETOOTH to "Bluetooth"
        ).filter { (route, _) -> (audio.supportedRouteMask and route) != 0 }
        if (routes.isEmpty()) {
            showError("No selectable audio outputs are available.")
            return
        }
        val choices = routes.map { (route, name) ->
            if (audio.route == route) "$name (current)" else name
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Call audio output")
            .setItems(choices) { _, position ->
                service.setAudioRoute(routes[position].first)
                CallSession.changed()
            }.setNegativeButton("Cancel", null)
            .show()
    }

    @android.annotation.TargetApi(34)
    private fun showModernAudioRoutes(service: PhoneInCallService) {
        val endpoints = service.availableEndpoints
        if (endpoints.isEmpty()) {
            showError("Android has not provided selectable audio devices yet.")
            return
        }
        val active = service.selectedEndpoint
        val choices = endpoints.map { endpoint ->
            val label = endpoint.endpointName.toString()
            if (endpoint.identifier == active?.identifier) "$label (current)" else label
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Call audio output")
            .setItems(choices) { _, index ->
                service.selectEndpoint(endpoints[index]) {
                    if (!isFinishing) showError("Android could not switch audio output.")
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDtmf(call: Call, body: LinearLayout) {
        val handler = android.os.Handler(mainLooper)
        var pendingStop: Runnable? = null
        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
        }
        listOf("123", "456", "789", "*0#").forEach { line ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.forEach { digit ->
                val key = action(digit.toString(), R.color.surface_tonal, R.color.text_main) {
                    // Cancel the previous stop so rapid digit entry cannot
                    // truncate the new tone. Always stop on dialog dismissal.
                    pendingStop?.let(handler::removeCallbacks)
                    call.playDtmfTone(digit)
                    val stop = Runnable { call.stopDtmfTone() }
                    pendingStop = stop
                    handler.postDelayed(stop, 180L)
                }
                key.contentDescription = "Send DTMF " + digit
                row.addView(key, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            pad.addView(row)
        }
        AlertDialog.Builder(this)
            .setTitle("In-call keypad")
            .setView(pad)
            .setNegativeButton("Close", null)
            .create()
            .apply {
                setOnDismissListener {
                    pendingStop?.let(handler::removeCallbacks)
                    call.stopDtmfTone()
                }
            }.show()
    }

    private fun renderRecents(body: LinearLayout) {
        body.addView(label("Recent calls", 21f, R.color.text_main, true))
        if (!allowed(Manifest.permission.READ_CALL_LOG)) {
            permissionCard(body, "Call history is only read with your permission.", Manifest.permission.READ_CALL_LOG, 202)
            return
        }
        val calls = queryRecentCalls()
        if (recentCalls == null) {
            body.addView(label("Loading recent calls from this device…", 15f, R.color.text_soft))
            return
        }
        if (recentsFailed) {
            body.addView(label("Android could not read call history. No call records were changed.",
                14f, R.color.text_soft))
            body.addView(action("Retry call history", R.color.surface_tonal, R.color.text_main) {
                retryRecentsRead()
            })
        }

        val options = listOf("All", "Missed", "Incoming", "Outgoing", "Declined")
        val filterStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { option ->
            val active = recentFilter == option
            val chip = action(option, if (active) R.color.brand else R.color.surface_tonal,
                if (active) R.color.on_brand else R.color.text_main) {
                recentFilter = option
                render()
            }.apply {
                textSize = 13f
                minHeight = dp(48)
                contentDescription = "$option call filter" + if (active) ", selected" else ""
            }
            val params = LinearLayout.LayoutParams(dp(98), dp(50)).apply {
                marginEnd = dp(5)
            }
            filterStrip.addView(chip, params)
        }
        body.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterStrip)
        })

        val search = EditText(this).apply {
            hint = "Find a number in recent calls"
            contentDescription = "Search recent call numbers"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setTextColor(color(R.color.text_main))
            setText(recentSearch)
            setSelection(text.length)
        }
        body.addView(search)
        val results = column(8).apply { setPadding(0, 0, 0, 0) }
        body.addView(results)
        fun filteredCalls(): List<Recent> = calls.asSequence()
            .filter { entry ->
                when (recentFilter) {
                    "Missed" -> entry.type == CallLog.Calls.MISSED_TYPE
                    "Incoming" -> entry.type == CallLog.Calls.INCOMING_TYPE
                    "Outgoing" -> entry.type == CallLog.Calls.OUTGOING_TYPE
                    "Declined" -> entry.type == CallLog.Calls.REJECTED_TYPE
                    else -> true
                }
            }
            .filter { it.visibleNumber().contains(recentSearch.trim(), ignoreCase = true) }
            .take(60)
            .toList()
        fun fillResults() {
            results.removeAllViews()
            val filtered = filteredCalls()
            if (filtered.isEmpty()) {
                results.addView(label("No matching recent calls.", 15f, R.color.text_soft))
            }
            filtered.forEach { entry ->
                val kind = when (entry.type) {
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.REJECTED_TYPE -> "Declined"
                    else -> "Call"
                }
                val number = entry.visibleNumber().ifBlank { "Private or unknown" }
                val card = column(8).apply { background = rounded(R.color.surface, 18) }
                card.addView(label(number, 17f, R.color.text_main, true))
                card.addView(label("$kind · ${DateUtils.getRelativeTimeSpanString(entry.whenMs)} · ${entry.seconds}s",
                    13f, R.color.text_soft))
                if (entry.visibleNumber().isNotBlank()) {
                    card.addView(twoActions("Call" to {
                        enteredNumber = entry.number
                        placeCall()
                    }, "Message" to { openSms(entry.number) }))
                }
                if (entry.visibleNumber().isNotBlank()) {
                    card.addView(action("Add to contacts", R.color.surface_tonal, R.color.text_main) {
                        createContact(entry.number)
                    })
                }
                results.addView(card)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recentSearch = s?.toString().orEmpty()
                fillResults()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fillResults()
    }

    private fun renderContacts(body: LinearLayout) {
        body.addView(label("Contacts", 21f, R.color.text_main, true))
        body.addView(action("Create contact", R.color.surface_tonal, R.color.text_main) {
            createContact(null)
        })
        if (!allowed(Manifest.permission.READ_CONTACTS)) {
            permissionCard(body,
                "Contacts stay on your device. Allow access to search and call them.",
                Manifest.permission.READ_CONTACTS, 201)
            return
        }
        val all = queryContacts()
        if (contacts == null) {
            body.addView(label("Loading contacts from this device…", 15f, R.color.text_soft))
            return
        }
        if (contactsFailed) {
            body.addView(label("Android could not read contacts. Your contacts were not changed.",
                14f, R.color.text_soft))
            body.addView(action("Retry contacts", R.color.surface_tonal, R.color.text_main) {
                retryContactsRead()
            })
        }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(false to "All contacts", true to "Favorites").forEach { (onlySaved, title) ->
            val selected = favoritesOnly == onlySaved
            val chip = action(title, if (selected) R.color.brand else R.color.surface_tonal,
                if (selected) R.color.on_brand else R.color.text_main) {
                favoritesOnly = onlySaved
                render()
            }.apply {
                isSelected = selected
                contentDescription = title + if (selected) ", selected" else ""
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        body.addView(filterRow)
        body.addView(label("Favorites are saved only on this device.", 13f, R.color.text_soft))
        val search = EditText(this).apply {
            hint = "Search contacts by name or number"
            contentDescription = "Search contacts"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(color(R.color.text_main))
            setText(contactFilter)
            setSelection(text.length)
        }
        body.addView(search)
        val results = column(8).apply { setPadding(0, 0, 0, 0) }
        body.addView(results)
        fun fillResults() {
            results.removeAllViews()
            val term = contactFilter.trim()
            val saved = favorites.all()
            val found = all.asSequence()
                .filter { !favoritesOnly || it.id.toString() in saved }
                .filter { entry ->
                    term.isEmpty() || entry.name.contains(term, ignoreCase = true) ||
                        entry.number.contains(term, ignoreCase = true) ||
                        (term.all(Char::isDigit) && PhoneMatcher.matches(term, entry.name, entry.number))
                }.sortedByDescending { it.id.toString() in saved }
                .take(70).toList()
            if (found.isEmpty()) {
                results.addView(label(if (favoritesOnly) "No matching favorites. Add a favorite from All contacts."
                    else "No matching phone contacts.", 15f, R.color.text_soft))
            }
            found.forEach { entry ->
                val card = column(8).apply { background = rounded(R.color.surface, 18) }
                card.addView(label(entry.name, 18f, R.color.text_main, true))
                card.addView(label(entry.number, 14f, R.color.text_soft))
                card.addView(twoActions("Call" to {
                    enteredNumber = entry.number
                    placeCall()
                }, "Message" to { openSms(entry.number) }))
                card.addView(action("View contact details", R.color.surface_tonal, R.color.text_main) {
                    viewContact(entry.id)
                }.apply { contentDescription = "View Android contact details for " + entry.name })
                val selected = entry.id.toString() in saved
                card.addView(action(if (selected) "Remove favorite" else "Add favorite",
                    R.color.surface_tonal, R.color.text_main) {
                    favorites.toggle(entry.id)
                    fillResults()
                }.apply {
                    contentDescription = (if (selected) "Remove " else "Add ") +
                        entry.name + (if (selected) " from favorites" else " to favorites")
                })
                results.addView(card)
            }
            if (term.isEmpty() && all.size > 70)
                results.addView(label("Showing 70 contacts. Search to find more.", 13f, R.color.text_soft))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                contactFilter = s?.toString().orEmpty()
                fillResults()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fillResults()
    }

    private fun renderSettings(body: LinearLayout) {
        body.addView(label("Phone app", 21f, R.color.text_main, true))
        val available = supportsPhoneRole()
        val active = isDefaultPhone()
        val status = column(16).apply { background = rounded(R.color.surface, 20) }
        val title = when {
            !available -> "Phone role is not available on this device"
            active -> "Default Phone app active"
            else -> "Default Phone app not selected"
        }
        status.addView(label(title, 19f, R.color.text_main, true))
        status.addView(label(
            if (!available) "Android does not offer a default Phone app role here. " +
                "Contacts and local UI remain usable, but cellular calling is unavailable."
            else if (active) "Android Telecom can connect incoming and ongoing calls to GoreeCloud Dialer."
            else "Choose GoreeCloud Dialer as your default Phone app to enable call handling.",
            14f, R.color.text_soft
        ))
        if (available) status.addView(action(
            if (active) "Manage default apps" else "Make default Phone app",
            R.color.brand, R.color.on_brand) {
            requestPhoneRole()
        })
        body.addView(status)
        body.addView(label("Optional local permissions", 20f, R.color.text_main, true).apply { topMargin(15) })
        permissionCard(body, "Contacts • offline T9 search and contact dialing", Manifest.permission.READ_CONTACTS, 201)
        val savedFavorites = favorites.all().size
        body.addView(action("Clear local favorites ($savedFavorites)",
            R.color.surface_tonal, R.color.text_main) {
            AlertDialog.Builder(this)
                .setTitle("Clear saved favorites?")
                .setMessage("This only removes GoreeCloud Dialer's local favorite selections. Contacts stay unchanged.")
                .setPositiveButton("Clear") { _, _ ->
                    favorites.clear()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }.apply { isEnabled = savedFavorites > 0 })
        body.addView(action("Manage Android app permissions", R.color.surface_tonal, R.color.text_main) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)))
            } catch (_: Exception) {
                showError("Android app settings could not be opened.")
            }
        })
        permissionCard(body, "Call history • device recents", Manifest.permission.READ_CALL_LOG, 202)
        if (Build.VERSION.SDK_INT >= 33) {
            permissionCard(body, "Notifications • incoming and ongoing call controls", Manifest.permission.POST_NOTIFICATIONS, 203)
        }
        body.addView(action("Clear Dialer local data", R.color.surface_tonal, R.color.text_main) {
            AlertDialog.Builder(this)
                .setTitle("Clear Dialer data on this device?")
                .setMessage(
                    "Clears favorite selections, keypad draft, search filters and Dialer caches. " +
                        "Android contacts, carrier call history, SIM settings and permissions are not deleted."
                )
                .setPositiveButton("Clear local data") { _, _ ->
                    favorites.clear()
                    enteredNumber = ""
                    contactFilter = ""
                    recentSearch = ""
                    recentFilter = "All"
                    favoritesOnly = false
                    invalidateProviderCaches()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        val privacy = column(10).apply { background = rounded(R.color.surface, 18) }
        privacy.addView(label("Privacy and safety", 18f, R.color.text_main, true))
        privacy.addView(label(
            "No Internet permission, contact uploads, recording, AI screening or tracking. " +
                "The platform controls SIM, emergency routing and call audio. " +
                "Use a secondary test phone until real call and emergency-routing review is complete.",
            14f, R.color.text_soft
        ))
        body.addView(privacy.apply { topMargin(14) })
    }

    /** Primary action stays reachable even when the keypad body must scroll. */
    private fun addCallBar() {
        val scale = resources.configuration.fontScale.coerceIn(1f, 3f)
        val height = dp(maxOf(64, (64f * scale).toInt()))
        val bar = LinearLayout(this).apply {
            setPadding(dp(22), dp(4), dp(22), dp(4))
        }
        val call = action("Call", R.color.positive, R.color.surface) { placeCall() }
        call.contentDescription = "Place phone call"
        bar.addView(call, LinearLayout.LayoutParams(-1, height))
        root.addView(bar)
    }

    private fun addDock() {
        val textScale = resources.configuration.fontScale.coerceIn(1f, 3f)
        val dockHeight = dp(maxOf(56, (56f * textScale).toInt()))
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(R.color.surface_tonal, 28)
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        listOf(
            "keypad" to "Keypad", "recents" to "Recents",
            "contacts" to "Contacts", "settings" to "Settings"
        ).forEach { (id, name) ->
            val selected = screen == id
            // Keep large visual labels on one line while preserving the full,
            // precise spoken tab name for TalkBack.
            val fontScale = resources.configuration.fontScale
            val visibleName = when {
                fontScale >= 2f && id == "keypad" -> "Keys"
                fontScale >= 2f && id == "settings" -> "More"
                fontScale >= 1.3f && id == "recents" -> "Calls"
                fontScale >= 1.3f && id == "contacts" -> "People"
                else -> name
            }
            val btn = action(visibleName, if (selected) R.color.brand else R.color.surface_tonal,
                if (selected) R.color.on_brand else R.color.text_main) {
                screen = id; render()
            }
            btn.textSize = 13f
            btn.isSelected = selected
            btn.contentDescription = "$name tab" + if (selected) ", selected" else ""
            dock.addView(btn, LinearLayout.LayoutParams(0, dockHeight, 1f))
        }
        val holder = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        val width = minOf(dp(560), resources.displayMetrics.widthPixels - dp(28))
        holder.addView(dock, LinearLayout.LayoutParams(width.coerceAtLeast(dp(200)), -2))
        root.addView(holder)
    }

    private fun permissionCard(body: LinearLayout, description: String, permission: String, code: Int) {
        val container = column(12).apply { background = rounded(R.color.surface, 18) }
        container.addView(label(description, 14f, R.color.text_soft))
        container.addView(action(if (allowed(permission)) "Allowed" else "Allow access",
            if (allowed(permission)) R.color.surface_tonal else R.color.brand,
            if (allowed(permission)) R.color.text_main else R.color.on_brand) {
            if (!allowed(permission)) requestPermissions(arrayOf(permission), code)
        }.apply { isEnabled = !allowed(permission) })
        body.addView(container)
    }

    private fun queryContacts(): List<Contact> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        contacts?.let { return it }
        if (!contactsLoading) {
            contactsLoading = true
            val generation = contactsGeneration.next()
            val signal = CancellationSignal()
            contactsCancellation = signal
            val accepted = providerExecutor.post {
                val list = mutableListOf<Contact>()
                var failed = false
                try {
                    val cursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
                        signal
                    )
                    list.addAll(SnapshotCursorReader.read(cursor, signal, 1500) { row ->
                        val number = row.getString(1).orEmpty()
                        val id = row.getLong(2)
                        if (number.isNotBlank() && id > 0L)
                            Contact(row.getString(0).orEmpty().ifBlank { number }, number, id)
                        else null
                    })
                } catch (_: RuntimeException) {
                    // Do not present a partly populated result as complete if
                    // an OEM provider fails during cursor iteration.
                    list.clear()
                    failed = true
                }
                runOnUiThread {
                    if (!contactsGeneration.isCurrent(generation) || isFinishing || isDestroyed) return@runOnUiThread
                    // Permissions can be revoked while the provider query runs.
                    if (!allowed(Manifest.permission.READ_CONTACTS)) {
                        contacts = emptyList()
                        contactsFailed = false
                    } else {
                        contacts = list
                        contactsFailed = failed
                    }
                    contactsCancellation = null
                    contactsLoading = false
                    // Do not tear down a focused number editor while the user
                    // is typing: the next edit refreshes the suggestion list.
                    val typingNumber = screen == "keypad" && currentFocus is EditText
                    val updateResults = if (typingNumber) refreshKeypadSuggestions else null
                    if (updateResults != null) {
                        updateResults()
                    } else if (screen == "contacts" || screen == "keypad" || screen == "call") {
                        render()
                    }
                }
            }
            if (!accepted) {
                // Never run provider reads on the UI thread when an OEM provider
                // hangs. Let the user retry after the bounded queue drains.
                contactsCancellation?.cancel()
                contactsCancellation = null
                contacts = emptyList()
                contactsLoading = false
                contactsFailed = true
            }
        }
        return emptyList()
    }

    private fun queryRecentCalls(): List<Recent> {
        if (!allowed(Manifest.permission.READ_CALL_LOG)) return emptyList()
        recentCalls?.let { return it }
        if (!recentsLoading) {
            recentsLoading = true
            val generation = recentsGeneration.next()
            val signal = CancellationSignal()
            recentsCancellation = signal
            val accepted = providerExecutor.post {
                val list = mutableListOf<Recent>()
                var failed = false
                try {
                    val cursor = contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                            CallLog.Calls.DATE, CallLog.Calls.DURATION,
                            CallLog.Calls.NUMBER_PRESENTATION),
                        null, null, "${CallLog.Calls.DATE} DESC", signal
                    )
                    list.addAll(SnapshotCursorReader.read(cursor, signal, 150) { row ->
                        Recent(row.getString(0).orEmpty(), row.getInt(1),
                            row.getLong(2), row.getLong(3), row.getInt(4))
                    })
                } catch (_: RuntimeException) {
                    list.clear()
                    failed = true
                }
                runOnUiThread {
                    if (!recentsGeneration.isCurrent(generation) || isFinishing || isDestroyed) return@runOnUiThread
                    if (!allowed(Manifest.permission.READ_CALL_LOG)) {
                        recentCalls = emptyList()
                        recentsFailed = false
                    } else {
                        recentCalls = list
                        recentsFailed = failed
                    }
                    recentsCancellation = null
                    recentsLoading = false
                    if (screen == "recents") render()
                }
            }
            if (!accepted) {
                recentsCancellation?.cancel()
                recentsCancellation = null
                recentCalls = emptyList()
                recentsLoading = false
                recentsFailed = true
            }
        }
        return emptyList()
    }

    private fun placeCall() {
        val number = PhoneMatcher.cleanNumber(enteredNumber)
        if (number == null || number == "+") {
            AlertDialog.Builder(this).setMessage("Enter a valid telephone number.").setPositiveButton("OK", null).show()
            return
        }
        val uri = Uri.fromParts("tel", number, null)
        val telecom: TelecomManager? = getSystemService(TelecomManager::class.java)
        if (telecom == null || !supportsPhoneRole()) {
            showError("Android does not support Phone calling on this device.")
            return
        }
        if (!isDefaultPhone()) {
            AlertDialog.Builder(this)
                .setTitle("Default Phone app required")
                .setMessage("Choose GoreeCloud Dialer as your default Phone app before calling. No call has been placed.")
                .setPositiveButton("Choose Phone app") { _, _ -> requestPhoneRole() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (!allowed(Manifest.permission.CALL_PHONE)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 204)
            return
        }
        try {
            // Official Android Telecom path includes platform emergency routing to the
            // preloaded system dialer when appropriate, without app-side guessing.
            telecom.placeCall(uri, Bundle.EMPTY)
            enteredNumber = "" // Avoid accidental re-dial after returning to keypad.
            screen = "call"
            render()
        } catch (_: SecurityException) {
            showError("Calling permission was denied by Android.")
        } catch (_: IllegalArgumentException) {
            showError("Android could not dial this number.")
        }
    }

    private fun isDefaultPhone(): Boolean {
        val telecom: TelecomManager? = getSystemService(TelecomManager::class.java)
        return telecom?.defaultDialerPackage == packageName
    }

    private fun supportsPhoneRole(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            return getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        }
        return getSystemService(TelecomManager::class.java) != null
    }

    private fun requestPhoneRole() {
        if (!supportsPhoneRole()) {
            showError("Android does not offer a default Phone app role on this device.")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                    ?: run { showError("Android Phone role service is unavailable."); return }
                if (!roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER), 301)
                } else {
                    startActivity(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
                }
            } else {
                startActivity(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName))
            }
        } catch (_: android.content.ActivityNotFoundException) {
            showError("Android Phone app settings could not be opened.")
        } catch (_: SecurityException) {
            showError("Android blocked the Phone app settings request.")
        }
    }

    /** Read-only details are displayed by the user's chosen contact provider. */
    private fun viewContact(id: Long) {
        if (id <= 0L || !allowed(Manifest.permission.READ_CONTACTS)) {
            showError("Contact access is not available.")
            return
        }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { showError("No contacts viewer is available.") }
    }

    /** Contacts edits belong to the user's chosen contact provider, not this app. */
    private fun createContact(number: String?) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            if (!number.isNullOrBlank())
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        try { startActivity(intent) }
        catch (_: Exception) { showError("No compatible contacts editor is available.") }
    }

    private fun openSms(number: String) {
        try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))) }
        catch (_: Exception) { showError("No messaging app is available.") }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun allowed(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(colorId: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color(colorId))
            cornerRadius = dp(radius).toFloat()
        }

    private fun column(gap: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(gap)
        setPadding(pad, pad, pad, pad)
    }

    private fun label(value: String, size: Float, colorId: Int, strong: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color(colorId))
            if (strong) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(3))
        }

    private fun action(value: String, backgroundColor: Int, foregroundColor: Int, onClick: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 16f
            setTextColor(color(foregroundColor))
            background = rounded(backgroundColor, 18)
            minHeight = dp(54)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener { onClick() }
        }

    private fun twoActions(left: Pair<String, () -> Unit>, right: Pair<String, () -> Unit>): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(left, right).forEach { (title, callback) ->
            val btn = action(title, R.color.surface_tonal, R.color.text_main, callback)
            val lp = LinearLayout.LayoutParams(0, dp(60), 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            row.addView(btn, lp)
        }
        return row
    }

    private fun View.topMargin(dpValue: Int) {
        // LayoutParams are assigned on insertion, so the view's translation is
        // only decorative; spacing remains part of the parent layout flow.
        setPadding(paddingLeft, paddingTop + dp(dpValue), paddingRight, paddingBottom)
    }

    companion object {
        private const val STATE_SCREEN = "goreecloud.dialer.state.SCREEN"
        private const val STATE_DRAFT = "goreecloud.dialer.state.DRAFT"
        private const val STATE_RECENT_SEARCH = "goreecloud.dialer.state.RECENT_SEARCH"
        private const val STATE_RECENT_FILTER = "goreecloud.dialer.state.RECENT_FILTER"
        private const val STATE_CONTACT_SEARCH = "goreecloud.dialer.state.CONTACT_SEARCH"
        private const val STATE_FAVORITES_ONLY = "goreecloud.dialer.state.FAVORITES_ONLY"
        const val EXTRA_CALL_SCREEN = "com.goreecloud.dialer.extra.CALL_SCREEN"
        const val EXTRA_DTMF = "com.goreecloud.dialer.extra.DTMF"
    }
}
