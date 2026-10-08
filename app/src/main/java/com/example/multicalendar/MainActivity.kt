package com.example.multicalendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MENU_TODAY = 1001
        private const val MENU_GREGORIAN = 1002
        private const val MENU_HIJRI = 1003
        private const val MENU_ETHIOPIAN = 1004
        private const val MENU_FONT_UP = 1005
        private const val MENU_FONT_DOWN = 1006
        private const val MENU_COLOR = 1007

        private const val PREFS = "calendar_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_THEME_COLOR = "theme_color"

        private const val DEFAULT_COLOR = 0xFF1976D2.toInt()

        // ⏱ Timing for Dhikr animation
        private const val DHIKR_VISIBLE_MS = 3500L   // how long each phrase stays
        private const val DHIKR_FADE_MS = 500L       // fade in/out duration
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvClock: TextView
    private lateinit var tvDhikrArabic: TextView
    private lateinit var tvDhikrEnglish: TextView
    private lateinit var dhikrContainer: android.widget.LinearLayout
    private lateinit var adapter: CalendarAdapter
    private val pages = mutableListOf<CalendarPage>()
    private var themeColor: Int = DEFAULT_COLOR

    // ---------- Clock ----------
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = SimpleDateFormat(
                "EEEE, dd MMM yyyy  •  HH:mm:ss",
                Locale.getDefault()
            ).format(Date())
            tvClock.text = now
            clockHandler.postDelayed(this, 1000L)
        }
    }

    // ---------- Dhikr Rotation ----------
    private val dhikrList = listOf(
        "سُبْحَانَ اللَّهِ"                        to "Glory be to Allah",
        "الْحَمْدُ لِلَّهِ"                        to "All praise is for Allah",
        "اللَّهُ أَكْبَرُ"                         to "Allah is the Greatest",
        "لَا إِلَٰهَ إِلَّا اللَّهُ"                to "There is no god but Allah",
        "أَسْتَغْفِرُ اللَّهَ"                     to "I seek forgiveness from Allah",
        "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ" to "There is no power except with Allah",
        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"     to "In the name of Allah, the Most Merciful",
        "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ"           to "O Allah, send blessings upon Muhammad"
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
        val config = resources.configuration
        config.fontScale = savedScale
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
        viewPager.setPageTransformer { page, position ->
            page.alpha = 0.5f + (1 - kotlin.math.abs(position)) * 0.5f
            page.scaleY = 0.85f + (1 - kotlin.math.abs(position)) * 0.15f
        }

        applyThemeColor(themeColor)

        // Start clock
        clockHandler.post(clockRunnable)

        // Start Dhikr rotation
        loadDhikr(dhikrIndex)
        dhikrHandler.postDelayed(dhikrRunnable, DHIKR_VISIBLE_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        dhikrHandler.removeCallbacks(dhikrRunnable)
    }

    // ---------- DHIKR ANIMATION ----------

    private fun loadDhikr(index: Int) {
        val (arabic, english) = dhikrList[index]
        tvDhikrArabic.text = arabic
        tvDhikrEnglish.text = english
    }

    private fun animateDhikrTransition() {
        val fadeOut = ObjectAnimator.ofFloat(dhikrContainer, "alpha", 1f, 0f).apply {
            duration = DHIKR_FADE_MS
            interpolator = AccelerateDecelerateInterpolator()
        }
        val slideOut = ObjectAnimator.ofFloat(
            dhikrContainer, "translationX", 0f, -120f
        ).apply {
            duration = DHIKR_FADE_MS
            interpolator = AccelerateDecelerateInterpolator()
        }

        fadeOut.start()
        slideOut.start()

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                dhikrIndex = (dhikrIndex + 1) % dhikrList.size
                loadDhikr(dhikrIndex)

                // Prepare to slide in from the right
                dhikrContainer.translationX = 120f

                val fadeIn = ObjectAnimator.ofFloat(
                    dhikrContainer, "alpha", 0f, 1f
                ).apply {
                    duration = DHIKR_FADE_MS
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val slideIn = ObjectAnimator.ofFloat(
                    dhikrContainer, "translationX", 120f, 0f
                ).apply {
                    duration = DHIKR_FADE_MS
                    interpolator = AccelerateDecelerateInterpolator()
                }
                fadeIn.start()
                slideIn.start()
            }
        })
    }

    // ---------- EXISTING METHODS (unchanged) ----------

    private fun initPages() {
        val today = Calendar.getInstance()
        pages.add(CalendarPage(
            CalendarType.GREGORIAN,
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1
        ))
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_TODAY, 0, R.string.today)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_FONT_UP, 1, R.string.font_up)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_FONT_DOWN, 2, R.string.font_down)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_COLOR, 3, R.string.color)
        menu.add(0, MENU_GREGORIAN, 4, R.string.gregorian)
        menu.add(0, MENU_HIJRI, 5, R.string.hijri)
        menu.add(0, MENU_ETHIOPIAN, 6, R.string.ethiopian)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed(); true
            }
            MENU_TODAY -> {
                pages.clear()
                initPages()
                adapter.notifyDataSetChanged()
                viewPager.setCurrentItem(0, true)
                true
            }
            MENU_GREGORIAN -> { viewPager.setCurrentItem(0, true); true }
            MENU_HIJRI     -> { viewPager.setCurrentItem(1, true); true }
            MENU_ETHIOPIAN -> { viewPager.setCurrentItem(2, true); true }
            MENU_FONT_UP   -> { changeFontScale(1.15f); true }
            MENU_FONT_DOWN -> { changeFontScale(0.87f); true }
            MENU_COLOR     -> { showColorPicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun changeFontScale(multiplier: Float) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getFloat(KEY_FONT_SCALE, 1f)
        val newScale = (current * multiplier).coerceIn(0.7f, 2.0f)
        prefs.edit().putFloat(KEY_FONT_SCALE, newScale).apply()
        recreate()
    }

    private fun showColorPicker() {
        val colors = arrayOf(
            "Blue"    to 0xFF1976D2.toInt(),
            "Green"   to 0xFF388E3C.toInt(),
            "Purple"  to 0xFF7B1FA2.toInt(),
            "Red"     to 0xFFD32F2F.toInt(),
            "Orange"  to 0xFFF57C00.toInt(),
            "Teal"    to 0xFF00796B.toInt(),
            "Pink"    to 0xFFC2185B.toInt(),
            "Indigo"  to 0xFF303F9F.toInt()
        )
        val names = colors.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose Theme Color")
            .setItems(names) { _, which ->
                val color = colors[which].second
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                prefs.edit().putInt(KEY_THEME_COLOR, color).apply()
                themeColor = color
                applyThemeColor(color)
                adapter.setThemeColor(color)
            }
            .show()
    }

    private fun applyThemeColor(color: Int) {
        toolbar.setBackgroundColor(color)
        findViewById<android.view.View>(R.id.appBar).setBackgroundColor(color)

        // Tabs
        tabLayout.setBackgroundColor(color)
        tabLayout.setSelectedTabIndicatorColor(Color.WHITE)

        // Status bar
        window.statusBarColor = color

        // Dhikr text uses accent color
        tvDhikrArabic.setTextColor(color)

        // If any page is visible, refresh so buttons pick up new color
        adapter.setThemeColor(color)
    }
}