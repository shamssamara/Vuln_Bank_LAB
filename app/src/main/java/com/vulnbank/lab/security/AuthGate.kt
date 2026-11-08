package com.vulnbank.lab.security

import android.content.Context

/** Lab-only, CLIENT-SIDE local gate (no server authorization). Hook with Frida. */
class AuthGate(private val context: Context) {
    fun isAuthenticated(): Boolean = context.getSharedPreferences("vulnbank_lab", Context.MODE_PRIVATE)
        .getBoolean("offline_vip_unlocked", false)
}
