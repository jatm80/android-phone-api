package com.jatm.androidphoneapi.capabilities

interface ClipboardProvider {
    fun read(): ClipboardContent
    fun write(text: String)
}

data class ClipboardContent(
    val text: String?,
    val hasContent: Boolean,
)
