package com.vulnbank.lab.security

import android.content.Context
import android.os.Build
import java.io.File

/** Lab-only root check: intentional client-side check for Frida practice. */
class RootGate(private val context: Context) {
    fun isRooted(): Boolean {
        val simulated = context.getSharedPreferences("vulnbank_lab", Context.MODE_PRIVATE)
            .getBoolean("simulate_root", false)
        return simulated || Build.TAGS.orEmpty().contains("test-keys") ||
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any { File(it).exists() }
    }
}
