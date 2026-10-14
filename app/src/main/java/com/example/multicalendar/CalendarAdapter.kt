package com.example.multicalendar

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multicalendar.converters.EthiopianConverter
import com.example.multicalendar.converters.HijriConverter
import java.util.Calendar
import java.util.Locale

class CalendarAdapter(
    private val pages: List<CalendarPage>,
    private var themeColor: Int,
    private val onNavigate: (Int, Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.PageVH>() {

    /**
     * Called by MainActivity when the user picks a new theme color.
     * Re-renders all pages so today's circle and Next button update.
     */
    fun setThemeColor(color: Int) {
        themeColor = color
        notifyDataSetChanged()
    }

    inner class PageVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvSubTitle: TextView = v.findViewById(R.id.tvSubTitle)
        val rvDays: RecyclerView = v.findViewById(R.id.rvDays)
        val btnPrev: TextView = v.findViewById(R.id.btnPrev)
        val btnNext: TextView = v.findViewById(R.id.btnNext)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_page, parent, false)
        return PageVH(v)
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        val page = pages[position]
        holder.rvDays.layoutManager = GridLayoutManager(holder.itemView.context, 7)
        val cells = buildCells(page)
        holder.rvDays.adapter = DayAdapter(cells, page.type)

        when (page.type) {
            CalendarType.GREGORIAN -> {
                val cal = Calendar.getInstance().apply { set(page.year, page.month - 1, 1) }
                val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                holder.tvTitle.text = "$monthName ${page.year}"
                holder.tvSubTitle.text = "Gregorian Calendar"
            }
            CalendarType.HIJRI -> {
                val name = HijriConverter.HijriDate(1, page.month, page.year)
                    .monthNames[page.month - 1]
                holder.tvTitle.text = "$name ${page.year} AH"
                holder.tvSubTitle.text = "Hijri Calendar"
            }
            CalendarType.ETHIOPIAN -> {
                val name = EthiopianConverter.EthiopianDate(1, page.month, page.year)
                    .monthNames[page.month - 1]
                holder.tvTitle.text = "$name ${page.year} EC"
                holder.tvSubTitle.text = "Ethiopian Calendar"
            }
        }

        // Apply theme color to the Next button
        val g = GradientDrawable().apply {
            cornerRadius = 42f
            setColor(themeColor)
        }
        holder.btnNext.background = g

        holder.btnPrev.setOnClickListener { onNavigate(position, -1) }
        holder.btnNext.setOnClickListener { onNavigate(position, 1) }
    }

    private fun buildCells(page: CalendarPage): List<DayCell> {
        val cells = mutableListOf<DayCell>()
        val today = Calendar.getInstance()
        val firstWeekday: Int
        val totalDays: Int

        when (page.type) {
            CalendarType.GREGORIAN -> {
                val cal = Calendar.getInstance().apply { set(page.year, page.month - 1, 1) }
                firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1
                totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            CalendarType.HIJRI -> {
                val greg = hijriMonthStart(page.year, page.month)
                firstWeekday = greg.get(Calendar.DAY_OF_WEEK) - 1
                totalDays = HijriConverter.daysInHijriMonth(page.month, page.year)
            }
            CalendarType.ETHIOPIAN -> {
                val greg = ethiopianMonthStart(page.year, page.month)
                firstWeekday = greg.get(Calendar.DAY_OF_WEEK) - 1
                totalDays = EthiopianConverter.daysInEthiopianMonth(page.month, page.year)
            }
        }

        repeat(firstWeekday) { cells.add(DayCell("", false, null)) }
        for (d in 1..totalDays) {
            val isToday = isToday(page, d, today)
            val holiday = HolidayData.getHoliday(page.type, page.month, d)
            cells.add(DayCell(d.toString(), isToday, holiday))
        }
        return cells
    }

    private fun isToday(page: CalendarPage, day: Int, today: Calendar): Boolean {
        return when (page.type) {
            CalendarType.GREGORIAN ->
                today.get(Calendar.YEAR) == page.year &&
                        today.get(Calendar.MONTH) + 1 == page.month &&
                        today.get(Calendar.DAY_OF_MONTH) == day
            CalendarType.HIJRI -> {
                val h = HijriConverter.fromGregorian(today)
                h.year == page.year && h.month == page.month && h.day == day
            }
            CalendarType.ETHIOPIAN -> {
                val e = EthiopianConverter.fromGregorian(today)
                e.year == page.year && e.month == page.month && e.day == day
            }
        }
    }

    private fun hijriMonthStart(hYear: Int, hMonth: Int): Calendar {
        val c = Calendar.getInstance().apply { set(hYear + 579, 0, 1) }
        val scan = c.clone() as Calendar
        scan.add(Calendar.DAY_OF_YEAR, -400)
        repeat(800) {
            val h = HijriConverter.fromGregorian(scan)
            if (h.year == hYear && h.month == hMonth && h.day == 1)
                return scan.clone() as Calendar
            scan.add(Calendar.DAY_OF_MONTH, 1)
        }
        return Calendar.getInstance()
    }

    private fun ethiopianMonthStart(eYear: Int, eMonth: Int): Calendar {
        val newYearGregYear = eYear + 7
        val c = Calendar.getInstance()
        val newYearDay = if ((newYearGregYear + 1) % 4 == 0) 12 else 11
        c.set(newYearGregYear, Calendar.SEPTEMBER, newYearDay)
        c.add(Calendar.DAY_OF_MONTH, (eMonth - 1) * 30)
        return c
    }

    data class DayCell(val label: String, val isToday: Boolean, val holiday: String?)

    inner class DayAdapter(
        private val cells: List<DayCell>,
        private val pageType: CalendarType
    ) : RecyclerView.Adapter<DayAdapter.DayVH>() {

        inner class DayVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvDay: TextView = v.findViewById(R.id.tvDay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_day, parent, false)
            return DayVH(v)
        }

        override fun getItemCount() = cells.size

        override fun onBindViewHolder(holder: DayVH, position: Int) {
            val c = cells[position]
            val tv = holder.tvDay
            tv.text = c.label

            if (c.label.isEmpty()) {
                tv.background = null
                tv.setOnClickListener(null)
                return
            }

            when {
                c.isToday -> {
                    tv.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(themeColor)
                    }
                    tv.setTextColor(Color.WHITE)
                    tv.setTypeface(null, Typeface.BOLD)
                }
                c.holiday != null -> {
                    val (fill, stroke, txt) = when (pageType) {
                        CalendarType.GREGORIAN -> Triple(0xFFE3F2FD.toInt(), 0xFF64B5F6.toInt(), 0xFF0D47A1.toInt())
                        CalendarType.HIJRI -> Triple(0xFFE8F5E9.toInt(), 0xFF66BB6A.toInt(), 0xFF1B5E20.toInt())
                        CalendarType.ETHIOPIAN -> Triple(0xFFFFEBEE.toInt(), 0xFFEF5350.toInt(), 0xFFB71C1C.toInt())
                    }
                    tv.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(fill)
                        setStroke(3, stroke)
                    }
                    tv.setTextColor(txt)
                    tv.setTypeface(null, Typeface.BOLD)
                }
                else -> {
                    tv.setBackgroundResource(R.drawable.bg_day_cell)
                    tv.setTextColor(0xFF1F2937.toInt())
                    tv.setTypeface(null, Typeface.NORMAL)
                }
            }

            tv.setOnClickListener {
                when {
                    c.holiday != null -> AlertDialog.Builder(tv.context)
                        .setTitle(c.holiday)
                        .setMessage("Holiday on ${c.label}")
                        .setPositiveButton("OK", null).show()
                    c.isToday -> AlertDialog.Builder(tv.context)
                        .setTitle("Today")
                        .setMessage("You are here")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }
}