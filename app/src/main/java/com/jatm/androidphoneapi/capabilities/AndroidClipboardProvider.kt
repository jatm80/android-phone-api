package com.jatm.androidphoneapi.capabilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

// On Android 10+ (API 29), reading the clipboard from a background context throws
// SecurityException unless the app has input focus. The embedded API server runs in a
// foreground service, so reads will typically fail on API 29+. We catch the exception
// and return empty content rather than crashing.
class AndroidClipboardProvider(
    private val context: Context,
) : ClipboardProvider {
    override fun read(): ClipboardContent {
        return try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
            ClipboardContent(
                text = text,
                hasContent = clip != null && clip.itemCount > 0,
            )
        } catch (_: SecurityException) {
            ClipboardContent(text = null, hasContent = false)
        }
    }

    override fun write(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("api", text)
        manager.setPrimaryClip(clip)
    }
}
