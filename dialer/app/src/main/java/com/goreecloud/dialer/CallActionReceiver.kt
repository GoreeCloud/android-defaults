package com.goreecloud.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.VideoProfile

/** Only explicit, non-exported notification actions reach this receiver. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallSession.foreground() ?: return
        when (intent.action) {
            ANSWER -> if (call.state == Call.STATE_RINGING) call.answer(VideoProfile.STATE_AUDIO_ONLY)
            DECLINE -> if (call.state == Call.STATE_RINGING) call.reject(false, null)
            END -> if (call.state != Call.STATE_DISCONNECTED) call.disconnect()
        }
    }

    companion object {
        const val ANSWER = "com.goreecloud.dialer.action.ANSWER"
        const val DECLINE = "com.goreecloud.dialer.action.DECLINE"
        const val END = "com.goreecloud.dialer.action.END"
    }
}
