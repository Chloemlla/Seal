package com.chloemlla.seal.util

import android.os.Build
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

private val dateFormatLock = Any()

private fun dateFormat(): DateFormat {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
}

fun Long.toLocalizedString(locale: Locale = Locale.getDefault()): String {
    return if (Build.VERSION.SDK_INT >= 26) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date.from(Instant.ofEpochMilli(this)))
    } else {
        synchronized(dateFormatLock) {
            dateFormat().format(Date(this))
        }
    }
}
