package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.GregorianCalendar
import java.util.Locale

internal fun formatWidgetMonthLabel(
    yearMonth: YearMonth,
    locale: Locale
): String {
    val monthYearPattern = (DateFormat.getDateInstance(DateFormat.LONG, locale) as? SimpleDateFormat)
        ?.toPattern()
        ?.toWidgetMonthYearPattern()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_MONTH_YEAR_PATTERN
    val date = GregorianCalendar(locale).apply {
        clear()
        set(yearMonth.year, yearMonth.monthValue - 1, 1)
    }.time

    return SimpleDateFormat(monthYearPattern, locale).format(date)
}

internal fun buildCalendarMonthWidgetState(
    monthLabel: String,
    currentMonth: YearMonth,
    today: LocalDate,
    locale: Locale,
    summaries: Map<LocalDate, DateTodoSummary>
): CalendarMonthWidgetState =
    CalendarMonthWidgetState(
        monthLabel = monthLabel,
        weekdayLabels = buildWeekdayLabels(locale),
        weeks = buildCalendarMonthWidgetDateGrid(
            yearMonth = currentMonth,
            locale = locale
        ).map { week ->
            week.map { cell ->
                cell.toWidgetDay(
                    summary = summaries[cell.date],
                    today = today
                )
            }
        }
    )

internal fun calendarMonthWidgetPresentationErrorState(
    monthLabel: String,
    locale: Locale
): CalendarMonthWidgetState =
    CalendarMonthWidgetState(
        monthLabel = monthLabel,
        weekdayLabels = buildWeekdayLabels(locale),
        weeks = emptyList(),
        isError = true
    )

private fun buildWeekdayLabels(locale: Locale): List<String> {
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    return List(WEEK_DAY_COUNT) { offset ->
        firstDayOfWeek
            .plus(offset.toLong())
            .getDisplayName(TextStyle.NARROW_STANDALONE, locale)
    }
}

private fun CalendarMonthWidgetDateCell.toWidgetDay(
    summary: DateTodoSummary?,
    today: LocalDate
): CalendarMonthWidgetDay =
    CalendarMonthWidgetDay(
        date = date,
        dayLabel = date.dayOfMonth.toString(),
        taskCountLabel = summary?.totalCount?.toTaskCountLabel(),
        todoChips = summary?.toTodoChips().orEmpty(),
        isCurrentMonth = isCurrentMonth,
        isToday = date == today
    )

private fun Int.toTaskCountLabel(): String? = when {
    this <= 0 -> null
    this > MAX_VISIBLE_TASK_COUNT -> "$MAX_VISIBLE_TASK_COUNT+"
    else -> toString()
}

private fun DateTodoSummary.toTodoChips(): List<CalendarMonthWidgetTodoChip> {
    val total = totalCount
    if (total <= 0) return emptyList()

    val visibleTodoCount = if (total > MAX_EXPANDED_TODO_LINES) {
        MAX_EXPANDED_TODO_LINES - 1
    } else {
        MAX_EXPANDED_TODO_LINES
    }
    val todoChips = todos
        .sortedWith(todoPreviewComparator)
        .take(visibleTodoCount)
        .map { todo ->
            CalendarMonthWidgetTodoChip(
                label = todo.title,
                isDone = todo.isDone
            )
        }

    return if (total > MAX_EXPANDED_TODO_LINES) {
        todoChips + CalendarMonthWidgetTodoChip(
            label = "+${total - todoChips.size}",
            isOverflow = true
        )
    } else {
        todoChips
    }
}

private val DateTodoSummary.totalCount: Int
    get() = indicatorCount + overflowCount

private val todoPreviewComparator: Comparator<TodoSummary> =
    compareBy<TodoSummary> { it.isDone }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenByDescending { it.priority.sortRank() }
        .thenByDescending { it.createdAt }

private fun TodoPriority.sortRank(): Int = when (this) {
    TodoPriority.HIGH -> 3
    TodoPriority.MEDIUM -> 2
    TodoPriority.LOW -> 1
}

internal fun String.toWidgetMonthYearPattern(): String {
    val tokens = parseDatePattern()
    val dayIndex = tokens.indexOfFirst { it is DatePatternToken.Field && it.symbol == DAY_OF_MONTH_PATTERN_SYMBOL }
    if (dayIndex == -1) return this

    return tokens
        .removeDayOfMonthToken(dayIndex)
        .trimOuterLiteralWhitespace()
        .mergeAdjacentLiterals()
        .joinToString(separator = "") { it.patternText }
}

private fun String.parseDatePattern(): List<DatePatternToken> {
    val tokens = mutableListOf<DatePatternToken>()
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == PATTERN_QUOTE -> {
                val quoted = StringBuilder()
                index++
                while (index < length) {
                    val quotedChar = this[index]
                    if (quotedChar == PATTERN_QUOTE) {
                        if (index + 1 < length && this[index + 1] == PATTERN_QUOTE) {
                            quoted.append(PATTERN_QUOTE)
                            index += 2
                        } else {
                            index++
                            break
                        }
                    } else {
                        quoted.append(quotedChar)
                        index++
                    }
                }
                tokens += DatePatternToken.Literal(quoted.toString())
            }

            char.isAsciiPatternLetter() -> {
                val start = index
                while (index < length && this[index] == char) index++
                tokens += DatePatternToken.Field(symbol = char, text = substring(start, index))
            }

            else -> {
                val start = index
                while (index < length && this[index] != PATTERN_QUOTE && !this[index].isAsciiPatternLetter()) {
                    index++
                }
                tokens += DatePatternToken.Literal(substring(start, index))
            }
        }
    }
    return tokens
}

private fun List<DatePatternToken>.removeDayOfMonthToken(dayIndex: Int): List<DatePatternToken> {
    val withoutDay = toMutableList()
    withoutDay.removeAt(dayIndex)
    when {
        dayIndex < withoutDay.size && withoutDay[dayIndex] is DatePatternToken.Literal -> {
            withoutDay.removeAt(dayIndex)
        }

        dayIndex > 0 && withoutDay[dayIndex - 1] is DatePatternToken.Literal -> {
            withoutDay.removeAt(dayIndex - 1)
        }
    }
    return withoutDay
}

private fun List<DatePatternToken>.trimOuterLiteralWhitespace(): List<DatePatternToken> {
    val trimmed = toMutableList()
    while (trimmed.firstOrNull()?.isBlankLiteral == true) {
        trimmed.removeAt(0)
    }
    while (trimmed.lastOrNull()?.isBlankLiteral == true) {
        trimmed.removeAt(trimmed.lastIndex)
    }

    (trimmed.firstOrNull() as? DatePatternToken.Literal)
        ?.trimStart()
        ?.let { trimmed[0] = it }
    (trimmed.lastOrNull() as? DatePatternToken.Literal)
        ?.trimEnd()
        ?.let { trimmed[trimmed.lastIndex] = it }

    return trimmed
}

private fun List<DatePatternToken>.mergeAdjacentLiterals(): List<DatePatternToken> {
    val merged = mutableListOf<DatePatternToken>()
    for (token in this) {
        val previous = merged.lastOrNull()
        if (token is DatePatternToken.Literal && previous is DatePatternToken.Literal) {
            merged[merged.lastIndex] = DatePatternToken.Literal(previous.text + token.text)
        } else {
            merged += token
        }
    }
    return merged
}

private val DatePatternToken.patternText: String
    get() = when (this) {
        is DatePatternToken.Field -> text
        is DatePatternToken.Literal -> text.quoteDatePatternLiteral()
    }

private fun String.quoteDatePatternLiteral(): String =
    if (isEmpty()) {
        this
    } else {
        "$PATTERN_QUOTE${replace("'", "''")}$PATTERN_QUOTE"
    }

private fun Char.isAsciiPatternLetter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z'

private val DatePatternToken.isBlankLiteral: Boolean
    get() = (this as? DatePatternToken.Literal)?.text?.isBlank() == true

private sealed interface DatePatternToken {
    data class Field(
        val symbol: Char,
        val text: String
    ) : DatePatternToken

    data class Literal(
        val text: String
    ) : DatePatternToken {
        fun trimStart(): Literal = copy(text = text.trimStart())

        fun trimEnd(): Literal = copy(text = text.trimEnd())
    }
}

private const val WEEK_DAY_COUNT = 7
private const val MAX_VISIBLE_TASK_COUNT = 9
private const val MAX_EXPANDED_TODO_LINES = 4
private const val DAY_OF_MONTH_PATTERN_SYMBOL = 'd'
private const val PATTERN_QUOTE = '\''
private const val DEFAULT_MONTH_YEAR_PATTERN = "MMMM y"
