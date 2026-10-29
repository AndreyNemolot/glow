package com.kitchen.clock.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// Сетка месяца и сдвиг выгорания считаются только из java.time.
object ClockFace {
    private val minuteFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val burnInSlots: List<BurnInOffset> = listOf(
        BurnInOffset(xDp = 8, yDp = 0),
        BurnInOffset(xDp = 0, yDp = 8),
        BurnInOffset(xDp = -8, yDp = 0),
        BurnInOffset(xDp = 0, yDp = -8),
    )

    fun minuteLabel(time: LocalDateTime): String = time.format(minuteFormatter)

    fun dateLine(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
        return capitalize(date.format(formatter), locale)
    }

    fun monthTitle(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
        return capitalize(date.format(formatter), locale)
    }

    fun weekdayLabels(locale: Locale = Locale.getDefault()): List<String> {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        return List(WEEK_LENGTH) { index ->
            val dayValue = ((firstDay.value - 1 + index) % WEEK_LENGTH) + 1
            DayOfWeek.of(dayValue)
                .getDisplayName(TextStyle.SHORT, locale)
                .trimEnd('.')
                .uppercase(locale)
        }
    }

    fun monthGrid(date: LocalDate, locale: Locale = Locale.getDefault()): List<DayCell> {
        val firstOfMonth = date.withDayOfMonth(1)
        val weekStart = WeekFields.of(locale).firstDayOfWeek
        var leadingBlanks = firstOfMonth.dayOfWeek.value - weekStart.value
        if (leadingBlanks < 0) {
            leadingBlanks += WEEK_LENGTH
        }
        val cells = ArrayList<DayCell>(leadingBlanks + firstOfMonth.lengthOfMonth() + WEEK_LENGTH)
        repeat(leadingBlanks) {
            cells += DayCell(day = null, isToday = false)
        }
        for (day in 1..firstOfMonth.lengthOfMonth()) {
            cells += DayCell(day = day, isToday = day == date.dayOfMonth)
        }
        while (cells.size % WEEK_LENGTH != 0) {
            cells += DayCell(day = null, isToday = false)
        }
        return cells
    }

    fun burnInOffset(time: LocalDateTime): BurnInOffset {
        val minuteOfDay = time.hour * 60 + time.minute
        val slot = minuteOfDay / 2 % burnInSlots.size
        return burnInSlots[slot]
    }

    private fun capitalize(text: String, locale: Locale): String {
        return text.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }

    private const val WEEK_LENGTH = 7
}

data class DayCell(
    val day: Int?,
    val isToday: Boolean,
)

data class BurnInOffset(
    val xDp: Int,
    val yDp: Int,
)
