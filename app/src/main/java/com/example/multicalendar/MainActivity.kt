package com.example.multicalendar

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.multicalendar.converters.EthiopianConverter
import com.example.multicalendar.converters.HijriConverter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "calendar_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_THEME_COLOR = "theme_color"

        private const val DEFAULT_COLOR = 0xFF1976D2.toInt()

        private const val DHIKR_VISIBLE_MS = 3500L
        private const val DHIKR_FADE_MS = 500L

        private const val ETH_LAT = 9.03
        private const val ETH_LNG = 38.74
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvClock: TextView
    private lateinit var tvDhikrArabic: TextView
    private lateinit var tvDhikrEnglish: TextView
    private lateinit var dhikrContainer: LinearLayout
    private lateinit var btnPrayerTimes: Button

    private lateinit var adapter: CalendarAdapter
    private val pages = mutableListOf<CalendarPage>()
    private var themeColor: Int = DEFAULT_COLOR

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = SimpleDateFormat(
                "EEEE, dd MMM yyyy  |  HH:mm:ss",
                Locale.getDefault()
            ).format(Date())
            tvClock.text = now
            clockHandler.postDelayed(this, 1000L)
        }
    }

    private val dhikrList = listOf(
        "\u0633\u064F\u0628\u0652\u062D\u064E\u0627\u0646\u064E \u0627\u0644\u0644\u0651\u064E\u0647\u0650"
                to "Glory be to Allah",
        "\u0627\u0644\u0652\u062D\u064E\u0645\u0652\u062F\u064F \u0644\u0650\u0644\u0651\u064E\u0647\u0650"
                to "All praise is for Allah",
        "\u0627\u0644\u0644\u0651\u064E\u0647\u064F \u0623\u064E\u0643\u0652\u0628\u064E\u0631\u064F"
                to "Allah is the Greatest",
        "\u0644\u064E\u0627 \u0625\u0650\u0644\u064E\u0670\u0647\u064E \u0625\u0650\u0644\u0651\u064E\u0627 \u0627\u0644\u0644\u0651\u064E\u0647\u064F"
                to "There is no god but Allah",
        "\u0623\u064E\u0633\u0652\u062A\u064E\u063A\u0652\u0641\u0650\u0631\u064F \u0627\u0644\u0644\u0651\u064E\u0647\u064E"
                to "I seek forgiveness from Allah"
    )
    private var dhikrIndex = 0
    private val dhikrHandler = Handler(Looper.getMainLooper())
    private val dhikrRunnable = object : Runnable {
        override fun run() {
            animateDhikrTransition()
            dhikrHandler.postDelayed(this, DHIKR_VISIBLE_MS + DHIKR_FADE_MS * 2)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedScale = prefs.getFloat(KEY_FONT_SCALE, 1f)
        val config = android.content.res.Configuration(resources.configuration)
        config.fontScale = savedScale
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        tvClock = findViewById(R.id.tvClock)
        tvDhikrArabic = findViewById(R.id.tvDhikrArabic)
        tvDhikrEnglish = findViewById(R.id.tvDhikrEnglish)
        dhikrContainer = findViewById(R.id.dhikrContainer)
        btnPrayerTimes = findViewById(R.id.btnPrayerTimes)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        themeColor = prefs.getInt(KEY_THEME_COLOR, DEFAULT_COLOR)

        initPages()
        adapter = CalendarAdapter(pages, themeColor) { pageIndex, delta ->
            shiftMonth(pageIndex, delta)
        }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = when (pages[pos].type) {
                CalendarType.GREGORIAN -> getString(R.string.gregorian)
                CalendarType.HIJRI -> getString(R.string.hijri)
                CalendarType.ETHIOPIAN -> getString(R.string.ethiopian)
            }
        }.attach()

        viewPager.offscreenPageLimit = 3

        applyThemeColor(themeColor)
        clockHandler.post(clockRunnable)

        loadDhikr(dhikrIndex)
        dhikrHandler.postDelayed(dhikrRunnable, DHIKR_VISIBLE_MS)

        btnPrayerTimes.setOnClickListener { showPrayerTimesDialog() }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        dhikrHandler.removeCallbacks(dhikrRunnable)
    }

    // ---------------- Dhikr ----------------

    private fun loadDhikr(index: Int) {
        val p = dhikrList[index]
        tvDhikrArabic.text = p.first
        tvDhikrEnglish.text = p.second
    }

    private fun animateDhikrTransition() {
        val fadeOut = android.animation.ObjectAnimator.ofFloat(
            dhikrContainer, "alpha", 1f, 0f
        ).apply { duration = DHIKR_FADE_MS }

        val slideOut = android.animation.ObjectAnimator.ofFloat(
            dhikrContainer, "translationX", 0f, -150f
        ).apply { duration = DHIKR_FADE_MS }

        fadeOut.start()
        slideOut.start()

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dhikrIndex = (dhikrIndex + 1) % dhikrList.size
                loadDhikr(dhikrIndex)

                dhikrContainer.translationX = 150f

                android.animation.ObjectAnimator.ofFloat(
                    dhikrContainer, "alpha", 0f, 1f
                ).apply { duration = DHIKR_FADE_MS }.start()

                android.animation.ObjectAnimator.ofFloat(
                    dhikrContainer, "translationX", 150f, 0f
                ).apply { duration = DHIKR_FADE_MS }.start()
            }
        })
    }

    // ---------------- Prayer Times ----------------

    private fun showPrayerTimesDialog() {
        val today = Calendar.getInstance()
        val prayers = PrayerTimes.calculate(today, ETH_LAT, ETH_LNG)
        val now = Calendar.getInstance()

        val header = TextView(this).apply {
            text = "Prayer Times — Addis Ababa"
            textSize = 16f
            setPadding(0, 20, 0, 6)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor)
        }

        val subHeader = TextView(this).apply {
            val fmt = SimpleDateFormat("h:mm a, EEEE", Locale.getDefault())
            text = "Your local time: ${fmt.format(now.time)}"
            textSize = 12f
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER
            setTextColor(0xFF6B7280.toInt())
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 0, 40, 30)
        }
        container.addView(header)
        container.addView(subHeader)

        val nextPrayer = prayers.firstOrNull { it.time.after(now) }

        for (p in prayers) {
            val isNext = (p.name == nextPrayer?.name)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
            }

            if (isNext) {
                row.background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(themeColor)
                }
            }

            val nameView = TextView(this).apply {
                text = p.name
                textSize = 15f
                setTextColor(if (isNext) Color.WHITE else 0xFF1F2937.toInt())
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val arabicView = TextView(this).apply {
                text = p.arabicName
                textSize = 15f
                setTextColor(if (isNext) Color.WHITE else themeColor)
                setPadding(0, 0, 20, 0)
            }

            val timeView = TextView(this).apply {
                text = PrayerTimes.format(p)
                textSize = 15f
                setTextColor(if (isNext) Color.WHITE else themeColor)
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(nameView)
            row.addView(arabicView)
            row.addView(timeView)
            container.addView(row)

            if (p != prayers.last()) {
                val div = View(this).apply {
                    setBackgroundColor(0xFFE3E7EF.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1
                    )
                }
                container.addView(div)
            }
        }

        val footer = TextView(this).apply {
            text = if (nextPrayer != null) {
                val diff = nextPrayer.time.timeInMillis - now.timeInMillis
                val h = diff / 3_600_000
                val m = (diff % 3_600_000) / 60_000
                "Next: ${nextPrayer.name} at ${PrayerTimes.format(nextPrayer)}  •  in ${h}h ${m}m"
            } else {
                "All prayers passed — next is Fajr tomorrow"
            }
            textSize = 13f
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER
            setTextColor(0xFF6B7280.toInt())
            setTypeface(null, Typeface.ITALIC)
        }
        container.addView(footer)

        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    // ---------------- Calendar ----------------

    private fun initPages() {
        val today = Calendar.getInstance()
        pages.add(
            CalendarPage(
                CalendarType.GREGORIAN,
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH) + 1
            )
        )
        val h = HijriConverter.fromGregorian(today)
        pages.add(CalendarPage(CalendarType.HIJRI, h.year, h.month))
        val e = EthiopianConverter.fromGregorian(today)
        pages.add(CalendarPage(CalendarType.ETHIOPIAN, e.year, e.month))
    }

    private fun shiftMonth(pageIndex: Int, delta: Int) {
        val p = pages[pageIndex]
        var newYear = p.year
        var newMonth = p.month + delta
        val monthsInYear = if (p.type == CalendarType.ETHIOPIAN) 13 else 12

        if (newMonth < 1) { newMonth = monthsInYear; newYear-- }
        if (newMonth > monthsInYear) { newMonth = 1; newYear++ }

        pages[pageIndex] = p.copy(year = newYear, month = newMonth)
        adapter.notifyItemChanged(pageIndex)
        syncOtherCalendars(pageIndex)
    }

    private fun syncOtherCalendars(sourceIndex: Int) {
        val src = pages[sourceIndex]
        val greg: Calendar = when (src.type) {
            CalendarType.GREGORIAN -> Calendar.getInstance().apply {
                set(src.year, src.month - 1, 1)
            }
            CalendarType.HIJRI -> hijriToGregorianApprox(src.year, src.month)
            CalendarType.ETHIOPIAN -> ethiopianToGregorian(src.year, src.month)
        }

        pages.forEachIndexed { i, p ->
            if (i == sourceIndex) return@forEachIndexed
            val newPage = when (p.type) {
                CalendarType.GREGORIAN -> CalendarPage(
                    CalendarType.GREGORIAN,
                    greg.get(Calendar.YEAR),
                    greg.get(Calendar.MONTH) + 1
                )
                CalendarType.HIJRI -> {
                    val h = HijriConverter.fromGregorian(greg)
                    CalendarPage(CalendarType.HIJRI, h.year, h.month)
                }
                CalendarType.ETHIOPIAN -> {
                    val e = EthiopianConverter.fromGregorian(greg)
                    CalendarPage(CalendarType.ETHIOPIAN, e.year, e.month)
                }
            }
            pages[i] = newPage
            adapter.notifyItemChanged(i)
        }
    }

    private fun hijriToGregorianApprox(hYear: Int, hMonth: Int): Calendar {
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

    private fun ethiopianToGregorian(eYear: Int, eMonth: Int): Calendar {
        val newYearGregYear = eYear + 7
        val c = Calendar.getInstance()
        val newYearDay = if ((newYearGregYear + 1) % 4 == 0) 12 else 11
        c.set(newYearGregYear, Calendar.SEPTEMBER, newYearDay)
        c.add(Calendar.DAY_OF_MONTH, (eMonth - 1) * 30)
        return c
    }

    // ---------------- Menu ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed(); true
            }
            R.id.action_today -> {
                pages.clear(); initPages()
                adapter.notifyDataSetChanged()
                viewPager.setCurrentItem(0, true); true
            }
            R.id.action_gregorian -> { viewPager.setCurrentItem(0, true); true }
            R.id.action_hijri -> { viewPager.setCurrentItem(1, true); true }
            R.id.action_ethiopian -> { viewPager.setCurrentItem(2, true); true }
            R.id.action_font_up -> { changeFontScale(1.15f); true }
            R.id.action_font_down -> { changeFontScale(0.87f); true }
            R.id.action_color -> { showColorPicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---------------- Font & Color ----------------

    private fun changeFontScale(multiplier: Float) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getFloat(KEY_FONT_SCALE, 1f)
        val newScale = (current * multiplier).coerceIn(0.7f, 2.0f)
        prefs.edit().putFloat(KEY_FONT_SCALE, newScale).apply()
        recreate()
    }

    private fun showColorPicker() {
        val colors = arrayOf(
            "Blue" to 0xFF1976D2.toInt(),
            "Green" to 0xFF388E3C.toInt(),
            "Purple" to 0xFF7B1FA2.toInt(),
            "Red" to 0xFFD32F2F.toInt(),
            "Orange" to 0xFFF57C00.toInt(),
            "Teal" to 0xFF00796B.toInt(),
            "Pink" to 0xFFC2185B.toInt(),
            "Indigo" to 0xFF303F9F.toInt()
        )
        val names = colors.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose Theme Color")
            .setItems(names) { _, which ->
                val color = colors[which].second
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putInt(KEY_THEME_COLOR, color).apply()
                themeColor = color
                applyThemeColor(color)
                adapter.setThemeColor(color)
            }
            .show()
    }

    private fun applyThemeColor(color: Int) {
        // Toolbar
        toolbar.setBackgroundColor(color)

        // Clock background (same blue bar)
        tvClock.setBackgroundColor(color)

        // Tabs
        tabLayout.setBackgroundColor(color)
        tabLayout.setSelectedTabIndicatorColor(Color.WHITE)

        // Status bar
        @Suppress("DEPRECATION")
        window.statusBarColor = color

        // Dhikr accent
        tvDhikrArabic.setTextColor(color)

        // Refresh calendar pages so today's circle & buttons pick up the new color
        adapter.setThemeColor(color)
    }
}