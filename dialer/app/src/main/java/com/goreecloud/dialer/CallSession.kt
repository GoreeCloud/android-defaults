package com.goreecloud.dialer

import android.os.Handler
import android.os.Looper
import android.telecom.Call

/** Android Telecom owns the calls; this process only maintains live UI references. */
object CallSession {
    private val handler = Handler(Looper.getMainLooper())
    private val calls = mutableListOf<Call>()
    private val tokens = LiveCallTokens<Call>()
    private val listeners = mutableSetOf<() -> Unit>()
    var service: PhoneInCallService? = null
        private set

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onCallDestroyed(call: Call) = remove(call)
        override fun onChildrenChanged(call: Call, children: MutableList<Call>?) = changed()
    }

    fun bind(value: PhoneInCallService) {
        if (service === value) return
        if (service != null) {
            // Telecom can replace a service instance while the previous one
            // is still tearing down. Drop its stale call references and
            // notification tokens; Android will deliver the new service's
            // active calls through onCallAdded with fresh tokens.
            service = null
            clear()
        }
        service = value
        changed()
    }

    fun unbind(value: PhoneInCallService) {
        // Android may recreate InCallService before an old instance finishes
        // teardown. Never let a stale service clear a newer service's calls.
        if (service !== value) return
        service = null
        clear()
    }
    fun add(call: Call) {
        if (calls.none { it === call }) {
            calls.add(call)
            tokens.register(call)
            call.registerCallback(callback, handler)
        }
        changed()
    }
    fun remove(call: Call) {
        // onCallDestroyed and onCallRemoved can both report this same call.
        // Only unregister and notify for its first actual removal.
        val index = calls.indexOfFirst { it === call }
        if (index < 0) return
        calls.removeAt(index)
        call.unregisterCallback(callback)
        tokens.remove(call)
        changed()
    }
    private fun clear() {
        calls.toList().forEach { it.unregisterCallback(callback) }
        calls.clear()
        tokens.clear()
        changed()
    }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun stopObserving(listener: () -> Unit) { listeners.remove(listener) }
    fun all(): List<Call> = calls.toList()

    /** A stale or replayed notification action must never act on another call. */
    fun token(call: Call): String? = tokens.token(call)
    fun callForToken(token: String?): Call? =
        tokens.resolve(token)?.takeIf { match -> calls.any { it === match } }

    fun foreground(): Call? =
        calls.firstOrNull { it.state == Call.STATE_RINGING } ?:
        calls.firstOrNull { it.state == Call.STATE_ACTIVE } ?:
        calls.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING } ?:
        calls.firstOrNull { it.state == Call.STATE_HOLDING } ?:
        calls.firstOrNull()

    fun changed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { changed() }
            return
        }
        listeners.toList().forEach { it() }
        service?.refreshNotification()
    }
}
