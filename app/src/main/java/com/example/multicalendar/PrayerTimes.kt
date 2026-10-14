package com.example.multicalendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object PrayerTimes {

    data class Prayer(
        val name: String,
        val arabicName: String,
        val time: Calendar
    )

    fun calculate(
        date: Calendar,
        latitude: Double,
        longitude: Double,
        timeZoneOffsetHours: Double = 3.0,
        fajrAngle: Double = 15.0,
        ishaAngle: Double = 15.0
    ): List<Prayer> {

        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)

        val jd = julianDate(year, month, day)
        val sun = sunPosition(jd - longitude / 360.0)
        val declination = sun.first
        val equationOfTime = sun.second

        val dhuhr = 12.0 + timeZoneOffsetHours - longitude / 15.0 - equationOfTime

        // All angles passed as POSITIVE. The sign is handled inside hourAngle.
        val fajrHA    = hourAngle(fajrAngle, latitude, declination)
        val sunriseHA = hourAngle(0.833, latitude, declination)
        val ishaHA    = hourAngle(ishaAngle, latitude, declination)
        val asrHA     = asrHourAngle(latitude, declination)

        val fajr    = dhuhr - fajrHA / 15.0
        val sunrise = dhuhr - sunriseHA / 15.0
        val asr     = dhuhr + asrHA / 15.0
        val maghrib = dhuhr + sunriseHA / 15.0
        val isha    = dhuhr + ishaHA / 15.0

        return listOf(
            Prayer("Fajr",    "الفجر",  hoursToCalendar(fajr, date)),
            Prayer("Sunrise", "الشروق", hoursToCalendar(sunrise, date)),
            Prayer("Dhuhr",   "الظهر",  hoursToCalendar(dhuhr, date)),
            Prayer("Asr",     "العصر",  hoursToCalendar(asr, date)),
            Prayer("Maghrib", "المغرب", hoursToCalendar(maghrib, date)),
            Prayer("Isha",    "العشاء", hoursToCalendar(isha, date))
        )
    }

    fun format(prayer: Prayer): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(prayer.time.time)

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y--; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        return (365.25 * (y + 4716)).toInt() +
                (30.6001 * (m + 1)).toInt() +
                day + b - 1524.5
    }

    private fun sunPosition(julianDay: Double): Pair<Double, Double> {
        val d = julianDay - 2451545.0

        val g = Math.toRadians((357.529 + 0.98560028 * d) % 360.0)
        val q = (280.459 + 0.98564736 * d) % 360.0

        val longitude = Math.toRadians(
            (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) % 360.0
        )

        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)

        val declination = Math.toDegrees(
            asin(sin(obliquity) * sin(longitude))
        )

        val rightAscension = Math.toDegrees(
            atan2(cos(obliquity) * sin(longitude), cos(longitude))
        ) / 15.0

        val equationOfTime = fixHour(q / 15.0) - fixHour(rightAscension)

        return Pair(declination, equationOfTime)
    }

    /**
     * Hour angle for a sun altitude BELOW the horizon.
     * Caller passes the angle as a POSITIVE number (e.g., 15.0 for Fajr).
     * Formula: cos(H) = (-sin(alt) - sin(lat)*sin(dec)) / (cos(lat)*cos(dec))
     * This is the standard convention used in prayer time algorithms.
     */
    private fun hourAngle(
        altitudeDegrees: Double,
        latitudeDegrees: Double,
        declinationDegrees: Double
    ): Double {
        val latR = Math.toRadians(latitudeDegrees)
        val decR = Math.toRadians(declinationDegrees)
        val altR = Math.toRadians(altitudeDegrees)

        val denominator = cos(latR) * cos(decR)
        if (abs(denominator) < 1e-10) return Double.NaN

        // Note the negative sign on sin(altitude):
        val cosH = (-sin(altR) - sin(latR) * sin(decR)) / denominator

        if (cosH < -1.0 || cosH > 1.0) return Double.NaN
        return Math.toDegrees(acos(cosH))
    }

    /**
     * Asr hour angle (Shafi'i madhhab, shadow factor = 1).
     * Uses the correct formula:
     *     altitude = atan(1 / (1 + tan(|lat - dec|)))
     * Then hour angle is solved with sin(altitude) POSITIVE.
     */
    private fun asrHourAngle(
        latitudeDegrees: Double,
        declinationDegrees: Double
    ): Double {
        val latR = Math.toRadians(latitudeDegrees)
        val decR = Math.toRadians(declinationDegrees)

        // Sun altitude at Asr time (positive, since sun is above horizon)
        val altitude = atan(1.0 / (1.0 + tan(abs(latR - decR))))

        val denominator = cos(latR) * cos(decR)
        if (abs(denominator) < 1e-10) return Double.NaN

        // sin(altitude) POSITIVE for Asr:
        val cosH = (sin(altitude) - sin(latR) * sin(decR)) / denominator

        if (cosH < -1.0 || cosH > 1.0) return Double.NaN
        return Math.toDegrees(acos(cosH))
    }

    private fun fixHour(hours: Double): Double {
        var result = hours % 24.0
        if (result < 0) result += 24.0
        return result
    }

    private fun hoursToCalendar(hours: Double, base: Calendar): Calendar {
        val result = base.clone() as Calendar
        result.set(Calendar.HOUR_OF_DAY, 0)
        result.set(Calendar.MINUTE, 0)
        result.set(Calendar.SECOND, 0)
        result.set(Calendar.MILLISECOND, 0)

        if (!hours.isFinite()) return result

        val totalMinutes = Math.round(hours * 60.0).toInt()
        result.add(Calendar.MINUTE, totalMinutes)
        return result
    }
}