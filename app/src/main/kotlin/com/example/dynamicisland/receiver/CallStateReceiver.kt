package com.example.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.dynamicisland.state.IslandEventBus
import com.example.dynamicisland.state.IslandState

class CallStateReceiver : BroadcastReceiver() {
    companion object { private var lastNumber = "" }

    override fun onReceive(context: Context?, intent: Intent?) {
        val state = intent?.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: lastNumber
        if (number.isNotEmpty()) lastNumber = number
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING ->
                IslandEventBus.tryEmit(IslandState.IncomingCall(callerName = resolveContactName(context, number), callerNumber = number))
            TelephonyManager.EXTRA_STATE_OFFHOOK ->
                IslandEventBus.tryEmit(IslandState.OngoingCall(callerName = resolveContactName(context, lastNumber), durationSeconds = 0L))
            TelephonyManager.EXTRA_STATE_IDLE -> { lastNumber = ""; IslandEventBus.tryEmit(IslandState.Dismissing) }
        }
    }

    private fun resolveContactName(context: Context?, number: String): String {
        if (context == null || number.isEmpty()) return number
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else number
            } ?: number
        }.getOrDefault(number)
    }
}
