package com.goreecloud.dialer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private var contacts: List<Contact>? = null
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
        readIntent(intent)
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
        contacts = null // Re-query only authorized local providers when screen next needs them.
        if (::root.isInitialized) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        contacts = null
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
        root.removeAllViews()
        val header = column(20).apply {
            setPadding(dp(22), dp(20), dp(22), dp(14))
        }
        header.addView(label("GoreeCloud", 13f, R.color.text_soft, true))
        header.addView(label(if (screen == "call") "On your call" else "Dialer", 32f, R.color.text_main, true))
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
        if (screen != "call") addDock()
        else body.addView(action("Back to keypad", R.color.surface_tonal, R.color.text_main) {
            screen = "keypad"; render()
        }.apply { topMargin(12) })
    }

    private fun renderKeypad(body: LinearLayout) {
        body.addView(label("Call a number", 17f, R.color.text_main, true))
        val numberEdit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            textSize = 26f
            setTextColor(color(R.color.text_main))
            hint = "Enter phone number"
            contentDescription = "Phone number"
            setText(enteredNumber)
            setSelection(text.length)
            setOnEditorActionListener { _, _, _ -> placeCall(); true }
        }
        val numberRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        numberRow.addView(numberEdit, LinearLayout.LayoutParams(0, dp(70), 1f))
        val delete = action("⌫", R.color.surface_tonal, R.color.text_main) {
            val cursor = numberEdit.selectionStart
            if (cursor > 0) numberEdit.text.delete(cursor - 1, cursor)
        }.apply {
            textSize = 24f
            contentDescription = "Delete previous digit. Long press to clear number"
            setOnLongClickListener { numberEdit.text.clear(); true }
        }
        numberRow.addView(delete, LinearLayout.LayoutParams(dp(70), dp(64)))
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
                val key = action(
                    if (letters.isEmpty()) digit else "$digit\n$letters",
                    R.color.surface, R.color.text_main
                ) { numberEdit.text.insert(numberEdit.selectionStart.coerceAtLeast(0), digit) }
                key.textSize = 23f
                key.minHeight = dp(69)
                if (digit == "0") {
                    key.setOnLongClickListener {
                        numberEdit.text.insert(numberEdit.selectionStart.coerceAtLeast(0), "+")
                        true
                    }
                    key.contentDescription = "Zero. Long press for plus"
                } else {
                    key.contentDescription = if (letters.isEmpty()) digit else "$digit, $letters"
                }
                val lp = LinearLayout.LayoutParams(0, dp(76), 1f)
                lp.setMargins(dp(3), dp(3), dp(3), dp(3))
                line.addView(key, lp)
            }
            body.addView(line)
        }
        val callButton = action("Call", R.color.positive, R.color.surface) { placeCall() }
        callButton.minHeight = dp(64)
        callButton.contentDescription = "Place phone call"
        body.addView(callButton.apply { topMargin(16) })
        body.addView(label("Emergency calls are handed to the system Phone app.", 13f, R.color.text_soft).apply { topMargin(9) })
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
        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
        }
        listOf("123", "456", "789", "*0#").forEach { line ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.forEach { digit ->
                val key = action(digit.toString(), R.color.surface_tonal, R.color.text_main) {
                    call.playDtmfTone(digit)
                    android.os.Handler(mainLooper).postDelayed({ call.stopDtmfTone() }, 200L)
                }
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
            .show()
    }

    private fun renderRecents(body: LinearLayout) {
        body.addView(label("Recent calls", 21f, R.color.text_main, true))
        if (!allowed(Manifest.permission.READ_CALL_LOG)) {
            permissionCard(body, "Call history is only read with your permission.", Manifest.permission.READ_CALL_LOG, 202)
            return
        }
        val calls = try {
            val result = mutableListOf<Recent>()
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
                    CallLog.Calls.DURATION, CallLog.Calls.NUMBER_PRESENTATION),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && result.size < 150) {
                    result.add(Recent(cursor.getString(0).orEmpty(), cursor.getInt(1),
                        cursor.getLong(2), cursor.getLong(3), cursor.getInt(4)))
                }
            }
            result
        } catch (_: SecurityException) { emptyList() }
          catch (_: IllegalArgumentException) { emptyList() }

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
        val active = isDefaultPhone()
        val status = column(16).apply { background = rounded(R.color.surface, 20) }
        status.addView(label(if (active) "Default Phone app active" else "Default Phone app not selected", 19f, R.color.text_main, true))
        status.addView(label(
            if (active) "Android Telecom can connect incoming and ongoing calls to GoreeCloud Dialer."
            else "Choose GoreeCloud Dialer as your default Phone app to enable call handling.",
            14f, R.color.text_soft
        ))
        status.addView(action(if (active) "Manage default apps" else "Make default Phone app", R.color.brand, R.color.on_brand) {
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
        permissionCard(body, "Call history • device recents", Manifest.permission.READ_CALL_LOG, 202)
        if (Build.VERSION.SDK_INT >= 33) {
            permissionCard(body, "Notifications • incoming and ongoing call controls", Manifest.permission.POST_NOTIFICATIONS, 203)
        }
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

    private fun addDock() {
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
            val btn = action(name, if (selected) R.color.brand else R.color.surface_tonal,
                if (selected) R.color.on_brand else R.color.text_main) {
                screen = id; render()
            }
            btn.textSize = 13f
            btn.contentDescription = "$name tab" + if (selected) ", selected" else ""
            dock.addView(btn, LinearLayout.LayoutParams(0, dp(56), 1f))
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
        val list = mutableListOf<Contact>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && list.size < 1500) {
                    val number = cursor.getString(1).orEmpty()
                    val id = cursor.getLong(2)
                    if (number.isNotBlank() && id > 0L)
                        list.add(Contact(cursor.getString(0).orEmpty().ifBlank { number }, number, id))
                }
            }
        } catch (_: SecurityException) {
            // Revocation or policy denial: treat the data as unavailable.
        } catch (_: IllegalArgumentException) {
            // Provider differences are nonfatal; calls still work without contacts.
        }
        contacts = list
        return list
    }

    private fun placeCall() {
        val number = PhoneMatcher.cleanNumber(enteredNumber)
        if (number == null || number == "+") {
            AlertDialog.Builder(this).setMessage("Enter a valid telephone number.").setPositiveButton("OK", null).show()
            return
        }
        val uri = Uri.fromParts("tel", number, null)
        val telecom = getSystemService(TelecomManager::class.java)
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
            screen = "call"
            render()
        } catch (_: SecurityException) {
            showError("Calling permission was denied by Android.")
        } catch (_: IllegalArgumentException) {
            showError("Android could not dial this number.")
        }
    }

    private fun isDefaultPhone(): Boolean {
        val telecom = getSystemService(TelecomManager::class.java)
        return telecom.defaultDialerPackage == packageName
    }

    private fun requestPhoneRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER), 301)
                } else {
                    startActivity(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
                }
                return
            }
        }
        startActivity(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName))
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
        const val EXTRA_CALL_SCREEN = "com.goreecloud.dialer.extra.CALL_SCREEN"
        const val EXTRA_DTMF = "com.goreecloud.dialer.extra.DTMF"
    }
}
