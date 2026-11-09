package com.goreecloud.dialer

import android.os.Handler
import android.os.Looper
import android.telecom.Call

/** Android Telecom owns the calls; this process only maintains live UI references. */
object CallSession {
    private val handler = Handler(Looper.getMainLooper())
    private val calls = mutableListOf<Call>()
    private val listeners = mutableSetOf<() -> Unit>()
    var service: PhoneInCallService? = null
        private set

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onCallDestroyed(call: Call) = remove(call)
        override fun onChildrenChanged(call: Call, children: MutableList<Call>?) = changed()
    }

    fun bind(value: PhoneInCallService) { service = value; changed() }
    fun unbind(value: PhoneInCallService) {
        if (service === value) service = null
        clear()
    }
    fun add(call: Call) {
        if (call !in calls) {
            calls.add(call)
            call.registerCallback(callback, handler)
        }
        changed()
    }
    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        changed()
    }
    private fun clear() {
        calls.toList().forEach { it.unregisterCallback(callback) }
        calls.clear()
        changed()
    }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun stopObserving(listener: () -> Unit) { listeners.remove(listener) }
    fun all(): List<Call> = calls.toList()
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
