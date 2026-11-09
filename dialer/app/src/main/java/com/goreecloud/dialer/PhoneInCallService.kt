package com.goreecloud.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.CallAudioState
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager

/**
 * Default-dialer Telecom UI implementation.
 * Android's Telecom framework owns ringing/audio/SIM/emergency decisions.
 */
class PhoneInCallService : InCallService() {
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    /** API 34+ endpoint snapshots from Telecom; never synthesize endpoints. */
    var availableEndpoints: List<CallEndpoint> = emptyList()
        private set
    var selectedEndpoint: CallEndpoint? = null
        private set
    var mutedOnModernAndroid = false
        private set

    @android.annotation.TargetApi(34)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        this.availableEndpoints = availableEndpoints.toList()
        CallSession.changed()
    }

    @android.annotation.TargetApi(34)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        selectedEndpoint = callEndpoint
        CallSession.changed()
    }

    @android.annotation.TargetApi(34)
    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        mutedOnModernAndroid = isMuted
        CallSession.changed()
    }

    @android.annotation.TargetApi(34)
    fun selectEndpoint(endpoint: CallEndpoint, onFailure: () -> Unit) {
        if (endpoint !in availableEndpoints) {
            onFailure()
            return
        }
        requestCallEndpointChange(endpoint, mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) {
                    CallSession.changed()
                }
                override fun onError(error: CallEndpointException) {
                    onFailure()
                }
            })
    }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Telephone calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming and active phone calls"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null) // Telecom/system owns call ringing.
            }
        )
        CallSession.bind(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSession.add(call)
    }

    override fun onCallRemoved(call: Call) {
        CallSession.remove(call)
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallSession.changed()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        startActivity(Intent(this, DialerActivity::class.java)
            .putExtra(DialerActivity.EXTRA_CALL_SCREEN, true)
            .putExtra(DialerActivity.EXTRA_DTMF, showDialpad)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onDestroy() {
        // A replaced Telecom service must not cancel its successor's ongoing
        // notification. CallSession.unbind() applies the same owner guard.
        if (CallSession.service === this) notifications.cancel(NOTIFICATION_ID)
        availableEndpoints = emptyList()
        selectedEndpoint = null
        CallSession.unbind(this)
        super.onDestroy()
    }

    fun refreshNotification() {
        val call = CallSession.foreground()
        if (call == null) {
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        val ringing = call.state == Call.STATE_RINGING
        val token = CallSession.token(call) ?: return
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, DialerActivity::class.java)
                .putExtra(DialerActivity.EXTRA_CALL_SCREEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(if (ringing) "Incoming phone call" else "Phone call")
            .setContentText(if (ringing) "Answer or decline" else "Tap for call controls")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(Notification.PRIORITY_HIGH)
        val publicVersion = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Phone call")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        builder.setPublicVersion(publicVersion)
        val decline = actionIntent(CallActionReceiver.DECLINE, 11, token)
        val answer = actionIntent(CallActionReceiver.ANSWER, 12, token)
        val hangup = actionIntent(CallActionReceiver.END, 13, token)
        if (Build.VERSION.SDK_INT >= 31) {
            // Native CallStyle is system-ranked on Android 12+. Keep caller data
            // out of the shade; callers with withheld numbers remain private.
            val caller = Person.Builder().setName("Phone call").build()
            builder.setStyle(if (ringing) {
                Notification.CallStyle.forIncomingCall(caller, decline, answer)
            } else {
                Notification.CallStyle.forOngoingCall(caller, hangup)
            })
        } else if (ringing) {
            builder.addAction(Notification.Action.Builder(null, "Decline", decline).build())
            builder.addAction(Notification.Action.Builder(null, "Answer", answer).build())
        } else {
            builder.addAction(Notification.Action.Builder(null, "End call", hangup).build())
        }
        if (ringing) {
            // Android 14+ user/system may deny full-screen access; the system
            // can still present the ordinary incoming-call notification.
            val fullscreenAllowed = Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent()
            if (fullscreenAllowed) builder.setFullScreenIntent(open, true)
        }
        try {
            notifications.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // A revoked notification permission should not crash Telecom.
            // The system's own fallback behavior must be tested on devices.
        }
    }

    private fun actionIntent(action: String, code: Int, token: String): PendingIntent =
        PendingIntent.getBroadcast(
            this, code,
            Intent(this, CallActionReceiver::class.java)
                .setAction(action)
                // Intent extras do not participate in PendingIntent identity; data does.
                .setData(Uri.parse("goreecloud-dialer://call-action/$token/$code"))
                .putExtra(CallActionReceiver.EXTRA_CALL_TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val CHANNEL = "calls"
        private const val NOTIFICATION_ID = 3001
    }
}
