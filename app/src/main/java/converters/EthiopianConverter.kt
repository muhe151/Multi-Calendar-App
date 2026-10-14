package com.example.multicalendar.converters

import java.util.Calendar

object EthiopianConverter {

    data class EthiopianDate(val day: Int, val month: Int, val year: Int) {
        val monthNames = listOf(
            "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit",
            "Megabit", "Miazia", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume"
        )
    }

    fun fromGregorian(cal: Calendar): EthiopianDate {
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        val newYearDay = if (isGregorianLeap(gYear + 1)) 12 else 11
        val etYear: Int
        val dayOfYearOffset: Int

        if (gMonth > 9 || (gMonth == 9 && gDay >= newYearDay)) {
            etYear = gYear - 7
            dayOfYearOffset = dayOfYear(gYear, gMonth, gDay) -
                    dayOfYear(gYear, 9, newYearDay)
        } else {
            etYear = gYear - 8
            dayOfYearOffset = dayOfYear(gYear, gMonth, gDay) +
                    daysInGregorianYear(gYear - 1) -
                    dayOfYear(gYear - 1, 9, if (isGregorianLeap(gYear)) 12 else 11)
        }

        val etMonth = dayOfYearOffset / 30 + 1
        val etDay = dayOfYearOffset % 30 + 1
        return EthiopianDate(etDay, etMonth, etYear)
    }

    fun daysInEthiopianMonth(month: Int, year: Int): Int {
        if (month == 13) return if (isEthiopianLeap(year)) 6 else 5
        return 30
    }

    private fun isEthiopianLeap(year: Int): Boolean = (year + 1) % 4 == 0

    private fun isGregorianLeap(y: Int) =
        (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    private fun daysInGregorianYear(y: Int) = if (isGregorianLeap(y)) 366 else 365

    private fun dayOfYear(y: Int, m: Int, d: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d)
        return cal.get(Calendar.DAY_OF_YEAR) - 1
    }
}