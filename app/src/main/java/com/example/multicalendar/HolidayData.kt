package com.example.multicalendar

object HolidayData {

    private val gregorianFixed = mapOf(
        "01-01" to "New Year's Day",
        "01-07" to "Ethiopian Christmas (Genna)",
        "01-19" to "Ethiopian Epiphany (Timkat)",
        "03-02" to "Adwa Victory Day",
        "05-01" to "International Labour Day",
        "05-05" to "Ethiopian Patriots' Day",
        "05-28" to "Derg Downfall Day",
        "09-11" to "Ethiopian New Year",
        "09-27" to "Meskel",
        "12-25" to "Christmas Day"
    )

    private val hijriFixed = mapOf(
        "01-01" to "Islamic New Year",
        "01-10" to "Day of Ashura",
        "03-12" to "Mawlid an-Nabi",
        "09-01" to "Start of Ramadan",
        "10-01" to "Eid al-Fitr",
        "12-10" to "Eid al-Adha"
    )

    private val ethiopianFixed = mapOf(
        "01-01" to "Ethiopian New Year",
        "01-17" to "Meskel",
        "04-29" to "Ethiopian Christmas",
        "05-11" to "Ethiopian Epiphany",
        "06-23" to "Adwa Victory Day",
        "09-01" to "International Labour Day",
        "09-05" to "Ethiopian Patriots' Day",
        "09-20" to "Derg Downfall Day"
    )

    fun getHoliday(type: CalendarType, month: Int, day: Int): String? {
        val key = "%02d-%02d".format(month, day)
        return when (type) {
            CalendarType.GREGORIAN -> gregorianFixed[key]
            CalendarType.HIJRI -> hijriFixed[key]
            CalendarType.ETHIOPIAN -> ethiopianFixed[key]
        }
    }
}