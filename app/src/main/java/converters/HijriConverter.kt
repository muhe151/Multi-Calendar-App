package com.example.multicalendar.converters

import java.util.Calendar
import kotlin.math.floor

object HijriConverter {

    data class HijriDate(val day: Int, val month: Int, val year: Int) {
        val monthNames = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
    }

    fun fromGregorian(cal: Calendar): HijriDate {
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        var jd = gregorianToJD(gYear, gMonth, gDay)
        jd = floor(jd) + 0.5

        var l = jd - 1948440 + 10632
        val n = floor((l - 1) / 10631.0)
        l = l - 10631 * n.toLong() + 354
        val j = (floor((10985 - l) / 5316.0)) * (floor((50 * l) / 17719.0)) +
                (floor(l / 5670.0)) * (floor((43 * l) / 15238.0))
        l = l - (floor((30 - j) / 15.0)) * (floor((17719 * j) / 50.0)) -
                (floor(j / 16.0)) * (floor((15238 * j) / 43.0)) + 29
        val hMonth = floor((24 * l) / 709.0).toInt()
        val hDay = (l - floor((709 * hMonth) / 24.0)).toInt()
        val hYear = (30 * n + j - 30).toInt()

        return HijriDate(hDay, hMonth, hYear)
    }

    private fun gregorianToJD(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    fun daysInHijriMonth(month: Int, year: Int): Int {
        if (month == 12 && isHijriLeap(year)) return 30
        return if (month % 2 == 1) 30 else 29
    }

    private fun isHijriLeap(year: Int): Boolean = ((year * 11 + 14) % 30) < 11
}