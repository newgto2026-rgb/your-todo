package com.neo.yourtodo.feature.todo.impl.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal val REMINDER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
internal val DUE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun reminderDateTimeToEpochMillis(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching {
        LocalDateTime.parse(value.trim(), REMINDER_FORMATTER)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal fun epochMillisToReminderDateTime(value: Long?): String {
    if (value == null) return ""
    return runCatching {
        Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(REMINDER_FORMATTER)
    }.getOrDefault("")
}

internal fun parseReminderInput(value: String): LocalDateTime? {
    if (value.isBlank()) return null
    return runCatching { LocalDateTime.parse(value, REMINDER_FORMATTER) }.getOrNull()
}

internal fun dueTimeTextToMinutes(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val time = LocalTime.parse(value.trim(), DUE_TIME_FORMATTER)
        time.hour * 60 + time.minute
    }.getOrNull()
}

internal fun minutesToDueTimeText(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val time = LocalTime.of(normalized / 60, normalized % 60)
    return time.format(DUE_TIME_FORMATTER)
}

internal fun dueDateTimeToEpochMillis(dueDate: LocalDate, dueTimeMinutes: Int): Long {
    val normalized = ((dueTimeMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val dueDateTime = dueDate.atTime(normalized / 60, normalized % 60)
    return dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

internal fun utcMillisToIsoDate(millis: Long?): String {
    if (millis == null) return ""
    return runCatching {
        Instant.ofEpochMilli(millis)
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrDefault("")
}

internal fun isoDateToUtcMillis(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}
