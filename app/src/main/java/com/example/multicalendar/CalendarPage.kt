package com.example.multicalendar

enum class CalendarType { GREGORIAN, HIJRI, ETHIOPIAN }

data class CalendarPage(
    val type: CalendarType,
    val year: Int,
    val month: Int
)