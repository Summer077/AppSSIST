package com.example.appssist

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.appssist.databinding.ActivityDashboardBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private var currentNav = "home"

    private var curriculums = listOf<CurriculumResponse>()
    private var sections = listOf<SectionResponse>()
    private var courses = listOf<CourseResponse>()
    
    private var currentUserFaculty: FacultyData? = null
    private var mySchedules = listOf<ScheduleItemResponse>()
    
    private var availableLevels = mutableListOf<Pair<Int, Int>>()

    private var selectedCurriculumId: Int? = null
    private var selectedYearLevel: Int? = null
    private var selectedSemester: Int? = null

    private var activeActionsView: View? = null
    private var isAuthFailureHandled = false

    // Track selected specializations for the multi-select dropdown
    private val selectedSpecIds = mutableSetOf<Int>()
    private val allCourseOptions = mutableListOf<CourseResponse>()

    // Staff Carousel Data
    private var currentCarouselIndex = 0
    private val carouselFeatures = listOf(
        Triple("Automated Schedule Generation", "Automatically generates class schedules based on room availability, faculty load, and subject requirements, ensuring conflict-free assignments.", R.drawable.staff_schedule),
        Triple("Conflict Detection and Validation", "Ensures all schedules are free from overlaps. The system alerts the admin of any time or room conflicts during manual adjustments.", R.drawable.staff_conflict),
        Triple("Faculty & Room Management", "Centralized management of faculty profiles, teaching loads, and classroom details for efficient scheduling and resource allocation.", R.drawable.staff_faculty),
        Triple("Real-time Schedule Updates", "Instantly reflects any schedule or room changes made by the admin, allowing faculty to view updated timetables in real time.", R.drawable.staff_realtime)
    )
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            currentCarouselIndex = (currentCarouselIndex + 1) % carouselFeatures.size
            updateStaffCarouselUI()
            carouselHandler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        RetrofitClient.init(this)
        
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check token before fetching data
        val token = TokenManager(this).getToken()
        if (token.isNullOrEmpty()) {
            redirectToLogin()
            return
        }

        fetchUserData()
        fetchDashboardData()

        setupQuickActions()
        setupBottomNavigation()
        setupCourseBackend()
        setupScheduleBackend()
        setupPrinting()
        setupProfileLogic()

        binding.layoutCourse.llMainContentCard.setOnClickListener {
            hideActiveActions()
        }

        loadRecentActivities()

    }
    private fun setupQuickActions() {
        binding.btnCreateSchedule.setOnClickListener {
            Toast.makeText(this, "Create Schedule clicked", Toast.LENGTH_SHORT).show()
        }
        binding.btnAddCourse.setOnClickListener {
            handleCourseAddClick()
        }
        binding.btnAddStaff.setOnClickListener {
            Toast.makeText(this, "Add Staff clicked", Toast.LENGTH_SHORT).show()
        }
        binding.btnAddSection.setOnClickListener {
            Toast.makeText(this, "Add Section clicked", Toast.LENGTH_SHORT).show()
        }
        binding.btnAddRoom.setOnClickListener {
            Toast.makeText(this, "Add Room clicked", Toast.LENGTH_SHORT).show()
        }
        
        // Stats Cards Listeners
        binding.cardTotalStaff.setOnClickListener {
            showStaffDialog()
        }
        binding.cardTotalSections.setOnClickListener {
            showSectionsDialog()
        }
    }

    private fun handleCourseAddClick() {
        if (selectedCurriculumId != null) {
            showAddCourseDialog()
        } else {
            Toast.makeText(this, "Please select a Curriculum first in the Course tab", Toast.LENGTH_SHORT).show()
            updateNavUI("course")
        }
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener { updateNavUI("home") }
        binding.navCourse.setOnClickListener { updateNavUI("course") }
        binding.navSchedule.setOnClickListener { updateNavUI("schedule") }
        binding.navProfile.setOnClickListener { updateNavUI("profile") }
        
        // Staff Nav Listeners
        val staffHome = findViewById<View>(R.id.nav_staff_home)
        val staffSchedule = findViewById<View>(R.id.nav_staff_schedule)
        val staffProfile = findViewById<View>(R.id.nav_staff_profile)
        
        staffHome?.setOnClickListener { updateNavUI("home") }
        staffSchedule?.setOnClickListener { updateNavUI("schedule") }
        staffProfile?.setOnClickListener { updateNavUI("profile") }
    }

    private fun setupCourseBackend() {
        binding.layoutCourse.btnAddCourseTop.setOnClickListener {
            handleCourseAddClick()
        }
        resetAcademicLevelSpinner()
    }

    private fun setupScheduleBackend() {
        // --- Admin Schedule Setup ---
        val adminTabs = listOf(
            binding.layoutSchedule.tabMySchedule,
            binding.layoutSchedule.tabFaculty,
            binding.layoutSchedule.tabSection,
            binding.layoutSchedule.tabRoom
        )

        adminTabs.forEach { tab ->
            tab.setBackgroundResource(R.drawable.bg_neumorphic_tab)
            tab.setOnClickListener {
                updateScheduleTabsUI(tab, adminTabs)
                when(tab.id) {
                    R.id.tab_my_schedule -> {
                        binding.layoutSchedule.llMyScheduleSection.visibility = View.VISIBLE
                        binding.layoutSchedule.llFacultySection.visibility = View.GONE
                        binding.layoutSchedule.llSectionSection.visibility = View.GONE
                        binding.layoutSchedule.llRoomSection.visibility = View.GONE
                        loadMySchedule(isAdmin = true)
                    }
                    R.id.tab_faculty -> {
                        binding.layoutSchedule.llMyScheduleSection.visibility = View.GONE
                        binding.layoutSchedule.llFacultySection.visibility = View.VISIBLE
                        binding.layoutSchedule.llSectionSection.visibility = View.GONE
                        binding.layoutSchedule.llRoomSection.visibility = View.GONE
                        loadFacultyListIntoSchedule()
                    }
                    R.id.tab_section -> {
                        binding.layoutSchedule.llMyScheduleSection.visibility = View.GONE
                        binding.layoutSchedule.llFacultySection.visibility = View.GONE
                        binding.layoutSchedule.llSectionSection.visibility = View.VISIBLE
                        binding.layoutSchedule.llRoomSection.visibility = View.GONE
                        loadSectionListIntoSchedule()
                    }
                    R.id.tab_room -> {
                        binding.layoutSchedule.llMyScheduleSection.visibility = View.GONE
                        binding.layoutSchedule.llFacultySection.visibility = View.GONE
                        binding.layoutSchedule.llSectionSection.visibility = View.GONE
                        binding.layoutSchedule.llRoomSection.visibility = View.VISIBLE
                        loadRoomListIntoSchedule()
                    }
                }
            }
        }
        binding.layoutSchedule.tabMySchedule.performClick()

        // Admin Day Selector
        val adminDays = listOf(
            binding.layoutSchedule.btnDayMon,
            binding.layoutSchedule.btnDayTue,
            binding.layoutSchedule.btnDayWed,
            binding.layoutSchedule.btnDayThurs,
            binding.layoutSchedule.btnDayFri,
            binding.layoutSchedule.btnDaySat
        )
        adminDays.forEach { dayView ->
            dayView.setOnClickListener {
                adminDays.forEach { it.isSelected = false }
                dayView.isSelected = true
                displayScheduleForDay(getDayName(dayView.id), isAdmin = true)
            }
        }
        binding.layoutSchedule.btnDayMon.performClick()

        // --- Staff Schedule Setup ---
        val staffDays = listOf(
            binding.layoutStaffSchedule.btnStaffDayMon,
            binding.layoutStaffSchedule.btnStaffDayTue,
            binding.layoutStaffSchedule.btnStaffDayWed,
            binding.layoutStaffSchedule.btnStaffDayThurs,
            binding.layoutStaffSchedule.btnStaffDayFri,
            binding.layoutStaffSchedule.btnStaffDaySat
        )
        staffDays.forEach { dayView ->
            dayView.setOnClickListener {
                staffDays.forEach { it.isSelected = false }
                dayView.isSelected = true
                displayScheduleForDay(getStaffDayName(dayView.id), isAdmin = false)
            }
        }
        binding.layoutStaffSchedule.btnStaffDayMon.performClick()
    }

    private fun getDayName(viewId: Int): String = when(viewId) {
        R.id.btn_day_mon -> "Monday"
        R.id.btn_day_tue -> "Tuesday"
        R.id.btn_day_wed -> "Wednesday"
        R.id.btn_day_thurs -> "Thursday"
        R.id.btn_day_fri -> "Friday"
        R.id.btn_day_sat -> "Saturday"
        else -> "Monday"
    }

    private fun getStaffDayName(viewId: Int): String = when(viewId) {
        R.id.btn_staff_day_mon -> "Monday"
        R.id.btn_staff_day_tue -> "Tuesday"
        R.id.btn_staff_day_wed -> "Wednesday"
        R.id.btn_staff_day_thurs -> "Thursday"
        R.id.btn_staff_day_fri -> "Friday"
        R.id.btn_staff_day_sat -> "Saturday"
        else -> "Monday"
    }

    private fun loadMySchedule(isAdmin: Boolean) {
        val facultyId = currentUserFaculty?.id ?: return
        if (facultyId <= 0) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getFacultySchedule(facultyId)
                mySchedules = response.schedules ?: emptyList()
                updateDayDots(isAdmin)
                
                val selectedDay = if (isAdmin) {
                    when {
                        binding.layoutSchedule.btnDayMon.isSelected -> "Monday"
                        binding.layoutSchedule.btnDayTue.isSelected -> "Tuesday"
                        binding.layoutSchedule.btnDayWed.isSelected -> "Wednesday"
                        binding.layoutSchedule.btnDayThurs.isSelected -> "Thursday"
                        binding.layoutSchedule.btnDayFri.isSelected -> "Friday"
                        binding.layoutSchedule.btnDaySat.isSelected -> "Saturday"
                        else -> "Monday"
                    }
                } else {
                    when {
                        binding.layoutStaffSchedule.btnStaffDayMon.isSelected -> "Monday"
                        binding.layoutStaffSchedule.btnStaffDayTue.isSelected -> "Tuesday"
                        binding.layoutStaffSchedule.btnStaffDayWed.isSelected -> "Wednesday"
                        binding.layoutStaffSchedule.btnStaffDayThurs.isSelected -> "Thursday"
                        binding.layoutStaffSchedule.btnStaffDayFri.isSelected -> "Friday"
                        binding.layoutStaffSchedule.btnStaffDaySat.isSelected -> "Saturday"
                        else -> "Monday"
                    }
                }
                displayScheduleForDay(selectedDay, isAdmin)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load schedule", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun getDayStringFromInt(dayInt: Int?): String {
        return when(dayInt) {
            0 -> "Monday"
            1 -> "Tuesday"
            2 -> "Wednesday"
            3 -> "Thursday"
            4 -> "Friday"
            5 -> "Saturday"
            6 -> "Sunday"
            else -> ""
        }
    }

    private fun updateDayDots(isAdmin: Boolean) {
        val dayViews = if (isAdmin) {
            mapOf(
                "Monday" to binding.layoutSchedule.btnDayMon,
                "Tuesday" to binding.layoutSchedule.btnDayTue,
                "Wednesday" to binding.layoutSchedule.btnDayWed,
                "Thursday" to binding.layoutSchedule.btnDayThurs,
                "Friday" to binding.layoutSchedule.btnDayFri,
                "Saturday" to binding.layoutSchedule.btnDaySat
            )
        } else {
            mapOf(
                "Monday" to binding.layoutStaffSchedule.btnStaffDayMon,
                "Tuesday" to binding.layoutStaffSchedule.btnStaffDayTue,
                "Wednesday" to binding.layoutStaffSchedule.btnStaffDayWed,
                "Thursday" to binding.layoutStaffSchedule.btnStaffDayThurs,
                "Friday" to binding.layoutStaffSchedule.btnStaffDayFri,
                "Saturday" to binding.layoutStaffSchedule.btnStaffDaySat
            )
        }

        dayViews.forEach { (dayName, view) ->
            val dotContainer = view.getChildAt(1) as? LinearLayout
            dotContainer?.removeAllViews()
            
            val daySchedules = mySchedules.filter { getDayStringFromInt(it.day).equals(dayName, ignoreCase = true) }
            daySchedules.forEach { schedule ->
                val dot = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (6 * resources.displayMetrics.density).toInt(),
                        (6 * resources.displayMetrics.density).toInt()
                    ).apply { setMargins(1, 1, 1, 1) }
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_circle_generic, theme)
                    val colorStr = schedule.courseColor ?: "#888888"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(colorStr))
                }
                dotContainer?.addView(dot)
            }
        }
    }

    private fun displayScheduleForDay(dayName: String, isAdmin: Boolean) {
        val gridContainer = if (isAdmin) binding.layoutSchedule.llTimetableGrid else binding.layoutStaffSchedule.llStaffTimetableGrid
        val cardsContainer = if (isAdmin) binding.layoutSchedule.flTimetableCards else binding.layoutStaffSchedule.flStaffTimetableCards
        val noScheduleState = if (isAdmin) binding.layoutSchedule.llNoScheduleState else binding.layoutStaffSchedule.llStaffNoScheduleState
        val timetableContainer = if (isAdmin) binding.layoutSchedule.rlTimetableContainer else binding.layoutStaffSchedule.rlStaffTimetableContainer

        gridContainer.removeAllViews()
        cardsContainer.removeAllViews()

        val daySchedules = mySchedules.filter { getDayStringFromInt(it.day).equals(dayName, ignoreCase = true) }

        if (daySchedules.isEmpty()) {
            noScheduleState.visibility = View.VISIBLE
            timetableContainer.visibility = View.GONE
        } else {
            noScheduleState.visibility = View.GONE
            timetableContainer.visibility = View.VISIBLE
            
            // Build the grid from 7:30 AM to 9:30 PM (21:30)
            var currentMinutes = 450 // 7:30 AM
            val endMinutes = 1290 // 9:30 PM
            
            while (currentMinutes <= endMinutes) {
                val rowView = LayoutInflater.from(this).inflate(R.layout.item_timetable_grid_row, gridContainer, false)
                val hour = currentMinutes / 60
                val min = currentMinutes % 60
                val ampm = if (hour >= 12) "PM" else "AM"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                
                rowView.findViewById<TextView>(R.id.tv_grid_time_label).text = String.format("%d:%02d %s", displayHour, min, ampm)
                gridContainer.addView(rowView)
                currentMinutes += 30
            }

            // Add the schedule cards
            daySchedules.forEach { schedule ->
                val startTimeStr = schedule.startTime ?: ""
                val endTimeStr = schedule.endTime ?: ""
                
                if (startTimeStr.isNotEmpty() && endTimeStr.isNotEmpty()) {
                    val startMin = timeToMinutes(startTimeStr)
                    val endMin = timeToMinutes(endTimeStr)
                    
                    val title = schedule.courseTitle ?: "No Title"
                    val range = "${formatTime(startTimeStr)} - ${formatTime(endTimeStr)} | ${schedule.sectionName ?: "N/A"}"
                    val roomName = "Room: ${schedule.roomName ?: "N/A"}"
                    val color = schedule.courseColor ?: "#888888"
                    
                    addGridScheduleItem(cardsContainer, gridContainer, startMin, endMin, title, range, roomName, color)
                }
            }
        }
    }

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            450
        }
    }

    private fun addGridScheduleItem(cardsContainer: FrameLayout, gridContainer: LinearLayout, startMin: Int, endMin: Int, title: String, range: String, room: String, color: String) {
        val cardView = LayoutInflater.from(this).inflate(R.layout.item_schedule_card, cardsContainer, false)
        
        cardView.findViewById<TextView>(R.id.tv_schedule_title).text = title
        cardView.findViewById<TextView>(R.id.tv_schedule_time_range).text = range
        cardView.findViewById<TextView>(R.id.tv_schedule_room).text = room
        
        val mainColor = Color.parseColor(color)
        val indicator = cardView.findViewById<View>(R.id.view_schedule_indicator)
        val indicatorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.bg_indicator_bar, theme) as GradientDrawable
        indicatorDrawable.setColor(mainColor)
        indicator.background = indicatorDrawable
        
        val lightColor = lightenColor(color, 0.85f)
        val contentBg = cardView.findViewById<LinearLayout>(R.id.ll_schedule_card_content)
        val contentDrawable = ResourcesCompat.getDrawable(resources, R.drawable.bg_schedule_card_round, theme) as GradientDrawable
        contentDrawable.setColor(Color.parseColor(lightColor))
        contentBg.background = contentDrawable
        
        // Match reference: each 30min row is 40dp. 
        val rowHeightDp = 40f
        val startFromGridMin = 450 // 7:30 AM
        
        // Each 30 mins = 40dp. 1 min = 40/30 dp.
        val topMarginDp = (startMin - startFromGridMin) * (rowHeightDp / 30f)
        val heightDp = (endMin - startMin) * (rowHeightDp / 30f)
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            heightDp.toInt().dpToPx()
        )
        // Adjusting topMargin to align perfectly with the horizontal lines
        params.topMargin = (topMarginDp + 20f).toInt().dpToPx()
        params.marginEnd = 8.dpToPx()
        
        cardView.layoutParams = params
        cardsContainer.addView(cardView)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun formatTime(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        return try {
            val parts = timeStr.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1]
            val ampm = if (hour >= 12) "PM" else "AM"
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            "$displayHour:$minute $ampm"
        } catch (e: Exception) {
            timeStr
        }
    }

    private fun lightenColor(colorStr: String, factor: Float): String {
        return try {
            val color = Color.parseColor(colorStr)
            val r = (Color.red(color) * factor + 255 * (1 - factor)).toInt()
            val g = (Color.green(color) * factor + 255 * (1 - factor)).toInt()
            val b = (Color.blue(color) * factor + 255 * (1 - factor)).toInt()
            String.format("#%02x%02x%02x", r, g, b)
        } catch (e: Exception) {
            "#F0F0F0"
        }
    }

    private fun loadFacultyListIntoSchedule() {
        val container = binding.layoutSchedule.llFacultyList
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val facultyList = RetrofitClient.apiService.getFacultyList()
                if (facultyList.isEmpty()) {
                    val tvEmpty = TextView(this@MainActivity).apply {
                        text = "No faculty found."
                        setTextColor(Color.GRAY)
                        gravity = Gravity.CENTER
                        setPadding(0, 100, 0, 0)
                        typeface = ResourcesCompat.getFont(this@MainActivity, R.font.lexend)
                    }
                    container.addView(tvEmpty)
                } else {
                    facultyList.forEach { faculty ->
                        val card = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_faculty_card, container, false)
                        
                        card.findViewById<TextView>(R.id.tv_faculty_name).text = "${faculty.first_name ?: ""} ${faculty.last_name ?: ""}"
                        card.findViewById<TextView>(R.id.tv_faculty_units).text = faculty.total_units.toString()
                        
                        val empStatus = when(faculty.employment_status?.lowercase()) {
                            "full_time" -> "Full-Time"
                            "part_time" -> "Part-Time"
                            "contractual" -> "Contractual"
                            else -> "Full-time"
                        }
                        card.findViewById<TextView>(R.id.tv_faculty_type).text = empStatus
                        card.findViewById<TextView>(R.id.tv_faculty_degree).text = faculty.highest_degree ?: "Masters"
                        card.findViewById<TextView>(R.id.tv_faculty_license).text = "Yes"

                        card.findViewById<TextView>(R.id.btn_view_schedule).setOnClickListener {
                            showCommonScheduleDialog("${faculty.first_name}'s Schedule", faculty.id, "faculty")
                        }

                        container.addView(card)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load faculty into schedule", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun showCommonScheduleDialog(title: String, resourceId: Int, type: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_faculty_schedule)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val height = (resources.displayMetrics.heightPixels * 0.90).toInt()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.window?.setGravity(Gravity.BOTTOM)

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_dialog_title)
        tvTitle.text = title

        val btnClose = dialog.findViewById<ImageView>(R.id.btn_close)
        btnClose.setOnClickListener { dialog.dismiss() }

        val btnPrint = dialog.findViewById<FrameLayout>(R.id.btn_print)
        val btnDownloadPdf = dialog.findViewById<FrameLayout>(R.id.btn_download_pdf)

        btnPrint.setOnClickListener { 
            printResourceSchedule(resourceId, type)
        }
        btnDownloadPdf.setOnClickListener { 
            printResourceSchedule(resourceId, type)
        }

        val daysContainer = dialog.findViewById<LinearLayout>(R.id.ll_days_container)
        val gridContainer = dialog.findViewById<LinearLayout>(R.id.ll_timetable_grid)
        val cardsContainer = dialog.findViewById<FrameLayout>(R.id.fl_timetable_cards)
        val noScheduleState = dialog.findViewById<LinearLayout>(R.id.ll_no_schedule_state)
        val timetableContainer = dialog.findViewById<RelativeLayout>(R.id.rl_timetable_container)

        val days = listOf("Mon", "Tue", "Wed", "Thurs", "Fri", "Sat")
        val fullDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        lifecycleScope.launch {
            try {
                // Determine which API to call based on type
                val scheduleResponse = when(type) {
                    "faculty" -> RetrofitClient.apiService.getFacultySchedule(resourceId)
                    "section" -> RetrofitClient.apiService.getSectionSchedule(resourceId)
                    "room" -> RetrofitClient.apiService.getRoomSchedule(resourceId)
                    else -> ScheduleListResponse(emptyList())
                }
                
                val schedules = scheduleResponse.schedules ?: emptyList()

                days.forEachIndexed { index, dayName ->
                    val dayView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_day_schedule_card, daysContainer, false)
                    dayView.findViewById<TextView>(R.id.tv_day_name).text = dayName
                    
                    val dotsContainer = dayView.findViewById<LinearLayout>(R.id.ll_dots_container)
                    val daySchedules = schedules.filter { getDayStringFromInt(it.day).equals(fullDays[index], ignoreCase = true) }
                    
                    daySchedules.forEach { schedule ->
                        val dot = View(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                (6 * resources.displayMetrics.density).toInt(),
                                (6 * resources.displayMetrics.density).toInt()
                            ).apply { setMargins(1, 1, 1, 1) }
                            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_circle_generic, theme)
                            val colorStr = schedule.courseColor ?: "#888888"
                            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(colorStr))
                        }
                        dotsContainer.addView(dot)
                    }

                    dayView.setOnClickListener {
                        for (i in 0 until daysContainer.childCount) {
                            daysContainer.getChildAt(i).isSelected = false
                        }
                        dayView.isSelected = true
                        displayCommonScheduleForDay(daySchedules, gridContainer, cardsContainer, noScheduleState, timetableContainer)
                    }
                    daysContainer.addView(dayView)
                }
                
                if (daysContainer.childCount > 0) {
                    daysContainer.getChildAt(0).performClick()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load schedule for dialog", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }

        dialog.show()
    }

    private fun displayCommonScheduleForDay(daySchedules: List<ScheduleItemResponse>, gridContainer: LinearLayout, cardsContainer: FrameLayout, noState: View, timetable: View) {
        gridContainer.removeAllViews()
        cardsContainer.removeAllViews()

        if (daySchedules.isEmpty()) {
            noState.visibility = View.VISIBLE
            timetable.visibility = View.GONE
        } else {
            noState.visibility = View.GONE
            timetable.visibility = View.VISIBLE
            
            var currentMinutes = 450 // 7:30 AM
            val endMinutes = 1290 // 9:30 PM
            
            while (currentMinutes <= endMinutes) {
                val rowView = LayoutInflater.from(this).inflate(R.layout.item_timetable_grid_row, gridContainer, false)
                val hour = currentMinutes / 60
                val min = currentMinutes % 60
                val ampm = if (hour >= 12) "PM" else "AM"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                
                rowView.findViewById<TextView>(R.id.tv_grid_time_label).text = String.format("%d:%02d %s", displayHour, min, ampm)
                gridContainer.addView(rowView)
                currentMinutes += 30
            }

            daySchedules.forEach { schedule ->
                val startTimeStr = schedule.startTime ?: ""
                val endTimeStr = schedule.endTime ?: ""
                
                if (startTimeStr.isNotEmpty() && endTimeStr.isNotEmpty()) {
                    val startMin = timeToMinutes(startTimeStr)
                    val endMin = timeToMinutes(endTimeStr)
                    
                    val title = schedule.courseTitle ?: "No Title"
                    val range = "${formatTime(startTimeStr)} - ${formatTime(endTimeStr)} | ${schedule.sectionName ?: "N/A"}"
                    val roomName = "Room: ${schedule.roomName ?: "N/A"}"
                    val color = schedule.courseColor ?: "#888888"
                    
                    addGridScheduleItem(cardsContainer, gridContainer, startMin, endMin, title, range, roomName, color)
                }
            }
        }
    }

    private fun printResourceSchedule(resourceId: Int, type: String) {
        lifecycleScope.launch {
            try {
                val responseBody = when(type) {
                    "faculty" -> RetrofitClient.apiService.getFacultyScheduleHtml(resourceId)
                    "section" -> RetrofitClient.apiService.getSectionScheduleHtml(resourceId)
                    "room" -> RetrofitClient.apiService.getRoomScheduleHtml(resourceId)
                    else -> throw Exception("Unknown type")
                }
                
                val htmlContent = responseBody.string()
                runOnUiThread {
                    doPrint(htmlContent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Print failed for $type", e)
                if (e is HttpException && e.code() == 401) {
                    handleSessionFailure("Unauthorized", true)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Print Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadSectionListIntoSchedule() {
        val container = binding.layoutSchedule.llSectionList
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val sectionList = RetrofitClient.apiService.getSections()
                if (sectionList.isEmpty()) {
                    val tvEmpty = TextView(this@MainActivity).apply {
                        text = "No sections found."
                        setTextColor(Color.GRAY)
                        gravity = Gravity.CENTER
                        setPadding(0, 100, 0, 0)
                        typeface = ResourcesCompat.getFont(this@MainActivity, R.font.lexend)
                    }
                    container.addView(tvEmpty)
                } else {
                    sectionList.forEach { section ->
                        val card = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_section_card, container, false)
                        
                        card.findViewById<TextView>(R.id.tv_section_name).text = section.name
                        
                        val yearStr = when(section.year_level) {
                            1 -> "1st Year"
                            2 -> "2nd Year"
                            3 -> "3rd Year"
                            4 -> "4th Year"
                            else -> "${section.year_level}th Year"
                        }
                        card.findViewById<TextView>(R.id.tv_year_level).text = yearStr
                        
                        val semStr = when(section.semester) {
                            1 -> "1st Sem"
                            2 -> "2nd Sem"
                            else -> "${section.semester}th Sem"
                        }
                        card.findViewById<TextView>(R.id.tv_semester).text = semStr
                        
                        card.findViewById<TextView>(R.id.tv_section_id).text = section.name.takeLast(2)
                        
                        val curriculumName = curriculums.find { it.id == section.curriculum }?.name ?: "BSCpE"
                        card.findViewById<TextView>(R.id.tv_curriculum).text = curriculumName

                        card.findViewById<TextView>(R.id.btn_view_schedule).setOnClickListener {
                            showCommonScheduleDialog("${section.name}'s Schedule", section.id, "section")
                        }

                        container.addView(card)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load sections into schedule", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun loadRoomListIntoSchedule() {
        val container = binding.layoutSchedule.llRoomList
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val roomList = RetrofitClient.apiService.getRooms()
                if (roomList.isEmpty()) {
                    val tvEmpty = TextView(this@MainActivity).apply {
                        text = "No rooms found."
                        setTextColor(Color.GRAY)
                        gravity = Gravity.CENTER
                        setPadding(0, 100, 0, 0)
                        typeface = ResourcesCompat.getFont(this@MainActivity, R.font.lexend)
                    }
                    container.addView(tvEmpty)
                } else {
                    roomList.forEach { room ->
                        val card = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_room_card, container, false)
                        
                        card.findViewById<TextView>(R.id.tv_room_name).text = room.name
                        card.findViewById<TextView>(R.id.tv_room_type).text = room.roomType
                        card.findViewById<TextView>(R.id.tv_room_capacity).text = room.capacity.toString()
                        card.findViewById<TextView>(R.id.tv_room_campus).text = room.campus
                        card.findViewById<TextView>(R.id.tv_room_number).text = room.roomNumber ?: room.name

                        card.findViewById<TextView>(R.id.btn_view_schedule).setOnClickListener {
                            showCommonScheduleDialog("${room.name}'s Schedule", room.id, "room")
                        }

                        container.addView(card)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load rooms into schedule", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun updateScheduleTabsUI(activeTab: TextView, allTabs: List<TextView>) {
        val activeTextColor = Color.parseColor("#E9ECEF")
        val inactiveTextColor = Color.parseColor("#1E2124")

        allTabs.forEach { tab ->
            if (tab == activeTab) {
                tab.isSelected = true
                tab.setTextColor(activeTextColor)
                tab.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tab.isSelected = false
                tab.setTextColor(inactiveTextColor)
                tab.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun hideActiveActions() {
        activeActionsView?.visibility = View.GONE
        activeActionsView = null
    }

    private fun fetchUserData() {
        lifecycleScope.launch {
            try {
                val data = RetrofitClient.apiService.getUserFacultyData()
                
                var finalData = data
                if (finalData.id <= 0) {
                    val facultyList = RetrofitClient.apiService.getFacultyList()
                    val matchingFaculty = facultyList.find { it.email?.trim()?.equals(finalData.email.trim(), ignoreCase = true) == true }
                    if (matchingFaculty != null) {
                        finalData = finalData.copy(id = matchingFaculty.id)
                    }
                }
                
                currentUserFaculty = finalData
                
                // Update UI Based on Role
                updateDashboardUIForRole()

                val greeting = if (finalData.first_name.isNotEmpty()) {
                    "Hello, ${finalData.first_name}!"
                } else {
                    "Hello!"
                }
                binding.tvGreeting.text = greeting
                
                // Update profile UI with data (Admin)
                binding.layoutProfile.tvProfileName.text = "${finalData.first_name} ${finalData.last_name}"
                binding.layoutProfile.tvProfileEmailSub.text = finalData.email
                binding.layoutProfile.etFirstName.setText(finalData.first_name)
                binding.layoutProfile.etLastName.setText(finalData.last_name)
                binding.layoutProfile.etEmail.setText(finalData.email)
                
                // Update profile UI with data (Staff)
                binding.layoutStaffProfile.tvStaffProfileName.text = "${finalData.first_name} ${finalData.last_name}"
                binding.layoutStaffProfile.tvStaffProfileEmailSub.text = finalData.email
                binding.layoutStaffProfile.etStaffFirstName.setText(finalData.first_name)
                binding.layoutStaffProfile.etStaffLastName.setText(finalData.last_name)
                binding.layoutStaffProfile.etStaffEmail.setText(finalData.email)
                
                // Gender Spinners
                val genderIndex = if (finalData.gender?.equals("F", ignoreCase = true) == true || finalData.gender?.equals("Female", ignoreCase = true) == true) 1 else 0
                binding.layoutProfile.spinnerGender.setSelection(genderIndex)
                binding.layoutStaffProfile.spinnerStaffGender.setSelection(genderIndex)

                // Employment Status Spinners
                val employmentOptions = listOf("Full-Time", "Part-Time", "Contractual")
                val empValue = when(finalData.employment_status?.lowercase()) {
                    "full_time" -> "Full-Time"
                    "part_time" -> "Part-Time"
                    "contractual" -> "Contractual"
                    else -> "Full-Time"
                }
                val empIndex = employmentOptions.indexOfFirst { it.equals(empValue, ignoreCase = true) }.let { if (it == -1) 0 else it }
                binding.layoutProfile.spinnerEmploymentStatus.setSelection(empIndex)
                binding.layoutStaffProfile.spinnerStaffEmploymentStatus.setSelection(empIndex)
                
                // Degree Spinners
                val degIndex = when {
                    finalData.highest_degree?.contains("Master", ignoreCase = true) == true -> 0
                    finalData.highest_degree?.contains("Doctor", ignoreCase = true) == true -> 1
                    else -> 0
                }
                binding.layoutProfile.spinnerDegree.setSelection(degIndex)
                binding.layoutStaffProfile.spinnerStaffDegree.setSelection(degIndex)
                
                // PRC License check mapping
                val isQualified = finalData.prc_licensed?.trim()?.equals("Yes", ignoreCase = true) == true
                binding.layoutProfile.cbQualified.isChecked = isQualified
                binding.layoutProfile.cbNa.isChecked = !isQualified
                binding.layoutStaffProfile.cbStaffQualified.isChecked = isQualified
                binding.layoutStaffProfile.cbStaffNa.isChecked = !isQualified

                // Specialization multi-select logic
                updateSpecializationCheckboxes()

                val email = currentUserFaculty?.email?.lowercase()?.trim() ?: ""
                loadMySchedule(isAdmin = email != "mpmariano@tip.edu.ph")
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    handleSessionFailure("Session expired. Please login again.", true)
                } else {
                    handleSessionFailure("Server Error: ${e.code()}")
                }
            } catch (e: Exception) {
                handleSessionFailure("Data Error: ${e.message}")
            }
        }
    }

    private fun updateDashboardUIForRole() {
        val email = currentUserFaculty?.email?.lowercase()?.trim() ?: ""
        val isStaff = email == "mpmariano@tip.edu.ph"
        
        if (isStaff) {
            binding.llStaffDashboardContent.root.visibility = View.VISIBLE
            binding.staffNavInclude.root.visibility = View.VISIBLE
            binding.llAdminDashboardContent.visibility = View.GONE
            binding.bottomNavContainer.visibility = View.GONE
            binding.navCourse.visibility = View.GONE
            
            val staffGreeting = findViewById<TextView>(R.id.tv_staff_greeting)
            staffGreeting?.text = "Hello, ${currentUserFaculty?.first_name} ${currentUserFaculty?.last_name}!"
            
            findViewById<Button>(R.id.btn_view_my_schedule_promo)?.setOnClickListener {
                updateNavUI("schedule")
            }
            
            startStaffCarousel()
        } else {
            binding.llStaffDashboardContent.root.visibility = View.GONE
            binding.staffNavInclude.root.visibility = View.GONE
            binding.llAdminDashboardContent.visibility = View.VISIBLE
            binding.bottomNavContainer.visibility = View.VISIBLE
            binding.navCourse.visibility = View.VISIBLE
            
            stopStaffCarousel()
        }
    }

    private fun startStaffCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable)
        carouselHandler.postDelayed(carouselRunnable, 5000)
        updateStaffCarouselUI()
    }

    private fun stopStaffCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable)
    }

    private fun updateStaffCarouselUI() {
        val feature = carouselFeatures[currentCarouselIndex]
        findViewById<TextView>(R.id.tv_staff_feature_title)?.text = feature.first
        findViewById<TextView>(R.id.tv_staff_feature_desc)?.text = feature.second
        findViewById<ImageView>(R.id.iv_staff_feature_logo)?.setImageResource(feature.third)
        
        val indicators = findViewById<LinearLayout>(R.id.ll_carousel_indicators)
        indicators?.let {
            for (i in 0 until it.childCount) {
                it.getChildAt(i).backgroundTintList = if (i == currentCarouselIndex) 
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1E2124"))
                    else android.content.res.ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
            }
        }
    }

    private fun updateSpecializationCheckboxes() {
        lifecycleScope.launch {
            try {
                // Fetch all courses
                val allCourses = RetrofitClient.apiService.getCourses(null, null, null)
                allCourseOptions.clear()
                allCourseOptions.addAll(allCourses.distinctBy { it.descriptive_title }.sortedBy { it.descriptive_title })
                
                // Initialize selected IDs from user data
                selectedSpecIds.clear()
                val userSpecs = currentUserFaculty?.specialization ?: emptyList()
                allCourseOptions.forEach { course ->
                    // Match by descriptive_title, course_code, or ID (as string)
                    if (userSpecs.any { 
                            it.trim().equals(course.descriptive_title.trim(), ignoreCase = true) || 
                            it.trim().equals(course.course_code.trim(), ignoreCase = true) ||
                            it.trim() == course.id.toString()
                        }) {
                        selectedSpecIds.add(course.id)
                    }
                }
                
                updateMultiSelectSpinner()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load specialization options", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun updateMultiSelectSpinner() {
        val selectedItems = allCourseOptions.filter { selectedSpecIds.contains(it.id) }
        val displayText = when {
            selectedItems.isEmpty() -> "Select Specialization"
            selectedItems.size == 1 -> selectedItems[0].course_code
            else -> "${selectedItems[0].course_code} (+${selectedItems.size - 1})"
        }
        
        val adapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, listOf(displayText))
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        
        // Update both spinners
        binding.layoutProfile.spinnerSpecialization.adapter = adapter
        binding.layoutStaffProfile.spinnerStaffSpecialization.adapter = adapter
        
        val touchListener = View.OnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                showCustomMultiSelectDialog()
            }
            true
        }
        
        binding.layoutProfile.spinnerSpecialization.setOnTouchListener(touchListener)
        binding.layoutStaffProfile.spinnerStaffSpecialization.setOnTouchListener(touchListener)
    }

    private fun showCustomMultiSelectDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_multi_select)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val height = (resources.displayMetrics.heightPixels * 0.70).toInt()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.window?.setGravity(Gravity.BOTTOM)

        val container = dialog.findViewById<LinearLayout>(R.id.ll_options_container)
        container.removeAllViews()

        // Temporary set to track changes before "Done" is clicked
        val tempSelectedIds = selectedSpecIds.toMutableSet()

        allCourseOptions.forEach { course ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_multi_select_option, container, false)
            val checkbox = itemView.findViewById<CheckBox>(R.id.checkbox_option)
            val tvCode = itemView.findViewById<TextView>(R.id.tv_option_code)
            val tvTitle = itemView.findViewById<TextView>(R.id.tv_option_title)

            tvCode.text = course.course_code
            tvTitle.text = course.descriptive_title
            checkbox.isChecked = tempSelectedIds.contains(course.id)

            itemView.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
                if (checkbox.isChecked) {
                    tempSelectedIds.add(course.id)
                } else {
                    tempSelectedIds.remove(course.id)
                }
            }

            container.addView(itemView)
        }

        dialog.findViewById<ImageView>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        
        dialog.findViewById<Button>(R.id.btn_done).setOnClickListener {
            selectedSpecIds.clear()
            selectedSpecIds.addAll(tempSelectedIds)
            updateMultiSelectSpinner()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun fetchDashboardData() {
        lifecycleScope.launch {
            try {
                val stats = RetrofitClient.apiService.getDashboardStats()
                binding.tvTotalStaff.text = stats.faculty_count.toString()
                binding.tvTotalSections.text = stats.section_count.toString()

                curriculums = RetrofitClient.apiService.getCurriculums()
                updateCurriculumSpinner()

                sections = RetrofitClient.apiService.getSections()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    handleSessionFailure("Session expired. Please login again.", true)
                } else {
                    Log.e("MainActivity", "Failed to fetch dashboard data", e)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to fetch dashboard data", e)
            }
        }
    }

    private fun showStaffDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_dashboard_list)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val tvTitle = dialog.findViewById<TextView>(R.id.tv_dialog_title)
        tvTitle.text = "Staff"
        
        val btnClose = dialog.findViewById<ImageView>(R.id.btn_close)
        btnClose.setOnClickListener { dialog.dismiss() }

        val container = dialog.findViewById<LinearLayout>(R.id.ll_list_container)
        container.removeAllViews()

        val tvStatus = TextView(this).apply {
            text = "Loading staff list..."
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 50)
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.lexend)
        }
        container.addView(tvStatus)

        dialog.show()
        
        val height = (resources.displayMetrics.heightPixels * 0.60).toInt()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.window?.setGravity(Gravity.BOTTOM)

        lifecycleScope.launch {
            try {
                val facultyList = RetrofitClient.apiService.getFacultyList()
                container.removeAllViews()
                
                if (facultyList.isEmpty()) {
                    tvStatus.text = "No staff members found in database."
                    container.addView(tvStatus)
                } else {
                    facultyList.forEach { faculty ->
                        val itemView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_dashboard_list, container, false)
                        itemView.findViewById<TextView>(R.id.tv_item_name).apply {
                            text = "${faculty.first_name ?: ""} ${faculty.last_name ?: ""}"
                            setTextColor(Color.BLACK)
                            visibility = View.VISIBLE
                        }
                        itemView.findViewById<TextView>(R.id.tv_item_sub).apply {
                            text = "Units: ${faculty.total_units}"
                            setTextColor(Color.parseColor("#555555"))
                            visibility = View.VISIBLE
                        }
                        container.addView(itemView)
                    }
                }
            } catch (e: Exception) {
                container.removeAllViews()
                tvStatus.text = "Error loading list"
                container.addView(tvStatus)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun showSectionsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_dashboard_list)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT) )
        
        val tvTitle = dialog.findViewById<TextView>(R.id.tv_dialog_title)
        tvTitle.text = "Sections"

        val btnClose = dialog.findViewById<ImageView>(R.id.btn_close)
        btnClose.setOnClickListener { dialog.dismiss() }

        val container = dialog.findViewById<LinearLayout>(R.id.ll_list_container)
        container.removeAllViews()

        if (sections.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "No sections found."
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                setPadding(0, 50, 0, 0)
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.lexend)
            }
            container.addView(tvEmpty)
        } else {
            sections.forEach { section ->
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_dashboard_list, container, false)
                itemView.findViewById<TextView>(R.id.tv_item_name).apply {
                    text = section.name
                    setTextColor(Color.BLACK)
                    visibility = View.VISIBLE
                }
                itemView.findViewById<TextView>(R.id.tv_item_sub).visibility = View.GONE
                
                val badge = itemView.findViewById<TextView>(R.id.tv_status_badge)
                badge.visibility = View.VISIBLE
                
                when (section.status.lowercase()) {
                    "complete" -> {
                        badge.text = "Complete Schedule"
                        badge.setBackgroundResource(R.drawable.bg_status_badge_green)
                    }
                    else -> {
                        badge.text = "No Schedule Yet"
                        badge.setBackgroundResource(R.drawable.bg_status_badge_red)
                    }
                }
                container.addView(itemView)
            }
        }
        
        dialog.show()
        
        val height = (resources.displayMetrics.heightPixels * 0.60).toInt()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    private fun updateCurriculumSpinner() {
        val curriculumNames = mutableListOf("Select Curriculum")
        curriculumNames.addAll(curriculums.map { "${it.name} (${it.year})" })
        
        val adapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, curriculumNames)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.layoutCourse.spinnerCurriculum.adapter = adapter

        binding.layoutCourse.spinnerCurriculum.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    selectedCurriculumId = null
                    selectedYearLevel = null
                    selectedSemester = null
                    resetAcademicLevelSpinner()
                    loadCourses()
                } else {
                    selectedCurriculumId = curriculums[position - 1].id
                    selectedYearLevel = null
                    selectedSemester = null
                    loadCoursesAndPopulateLevels()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun resetAcademicLevelSpinner() {
        availableLevels.clear()
        val levels = listOf("All Year Levels")
        val levelAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, levels)
        levelAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.layoutCourse.spinnerAcademicLevel.adapter = levelAdapter
    }

    private fun loadCoursesAndPopulateLevels() {
        lifecycleScope.launch {
            try {
                val allCourses = RetrofitClient.apiService.getCourses(selectedCurriculumId, null, null)
                courses = allCourses
                
                availableLevels = allCourses.map { Pair(it.year_level, it.semester) }.distinct().sortedWith(compareBy({ it.first }, { it.second })).toMutableList()
                
                val levelNames = mutableListOf("All Year Levels")
                availableLevels.forEach { (year, sem) ->
                    val yearStr = when(year) {
                        1 -> "1st"
                        2 -> "2nd"
                        3 -> "3rd"
                        4 -> "4th"
                        else -> "${year}th"
                    }
                    val semStr = when(sem) {
                        1 -> "1st"
                        2 -> "2nd"
                        else -> "${sem}th"
                    }
                    levelNames.add("$yearStr Year, $semStr Sem")
                }
                
                val levelAdapter = ArrayAdapter(this@MainActivity, R.layout.item_spinner_selected, android.R.id.text1, levelNames)
                levelAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                binding.layoutCourse.spinnerAcademicLevel.adapter = levelAdapter

                binding.layoutCourse.spinnerAcademicLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position == 0) {
                            selectedYearLevel = null
                            selectedSemester = null
                        } else {
                            val (year, sem) = availableLevels[position - 1]
                            selectedYearLevel = year
                            selectedSemester = sem
                        }
                        loadCourses()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                loadCourses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load courses for levels", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun loadCourses() {
        lifecycleScope.launch {
            try {
                val fetchedCourses = RetrofitClient.apiService.getCourses(selectedCurriculumId, selectedYearLevel, selectedSemester)
                courses = fetchedCourses
                
                val container = binding.layoutCourse.llCourseList
                container.removeAllViews()

                if (courses.isEmpty()) {
                    binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
                    binding.layoutCourse.llDynamicContent.visibility = View.GONE
                } else {
                    binding.layoutCourse.tvEmptyState.visibility = View.GONE
                    binding.layoutCourse.llDynamicContent.visibility = View.VISIBLE

                    // Group courses by Year/Semester
                    val groupedCourses = courses.groupBy { Pair(it.year_level, it.semester) }
                        .toSortedMap(compareBy({ it.first }, { it.second }))

                    groupedCourses.forEach { (level, courseList) ->
                        val (year, sem) = level
                        val yearStr = when(year) {
                            1 -> "1st"
                            2 -> "2nd"
                            3 -> "3rd"
                            4 -> "4th"
                            else -> "${year}th"
                        }
                        val semStr = when(sem) {
                            1 -> "1st"
                            2 -> "2nd"
                            else -> "${sem}th"
                        }
                        val totalUnits = courseList.sumOf { it.credit_units }
                        
                        // Add Header for this group
                        val headerView = TextView(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, (if (container.childCount > 0) 25 else 0).dpToPx(), 0, 0)
                            }
                            setBackgroundColor(Color.parseColor("#1E2124"))
                            setTextColor(Color.WHITE)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.lexend)
                            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                            gravity = Gravity.CENTER
                            text = "$yearStr Year, $semStr Semester ($totalUnits Units)"
                        }
                        container.addView(headerView)

                        // Add Courses for this group
                        courseList.forEach { course ->
                            val itemView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_course, container, false)
                            // Add horizontal padding only to course items
                            val contentLayout = itemView.findViewById<LinearLayout>(R.id.ll_course_item_root)
                            contentLayout.setPadding(20.dpToPx(), 0, 20.dpToPx(), 0)

                            itemView.findViewById<TextView>(R.id.tv_course_code).text = course.course_code
                            itemView.findViewById<TextView>(R.id.tv_course_title).text = course.descriptive_title
                            itemView.findViewById<TextView>(R.id.tv_course_details).text = "${course.credit_units} Units | Lec: ${course.lecture_hours} Lab: ${course.laboratory_hours}"
                            
                            val layoutActions = itemView.findViewById<LinearLayout>(R.id.ll_course_actions)
                            
                            itemView.setOnClickListener {
                                if (layoutActions.visibility == View.VISIBLE) {
                                    layoutActions.visibility = View.GONE
                                    activeActionsView = null
                                } else {
                                    hideActiveActions()
                                    layoutActions.visibility = View.VISIBLE
                                    activeActionsView = layoutActions
                                }
                            }

                            itemView.findViewById<ImageButton>(R.id.btn_edit_course).setOnClickListener {
                                showEditCourseDialog(course)
                                layoutActions.visibility = View.GONE
                            }
                            
                            itemView.findViewById<ImageButton>(R.id.btn_delete_course).setOnClickListener {
                                showDeleteCourseConfirmation(course)
                                layoutActions.visibility = View.GONE
                            }

                            container.addView(itemView)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load courses", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun showAddCourseDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_course)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        
        val etCode = dialog.findViewById<EditText>(R.id.et_course_code)
        val etTitle = dialog.findViewById<EditText>(R.id.et_descriptive_title)
        val etLec = dialog.findViewById<EditText>(R.id.et_lec_hours)
        val etLab = dialog.findViewById<EditText>(R.id.et_lab_hours)
        val etUnits = dialog.findViewById<EditText>(R.id.et_credit_hours)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val code = etCode.text.toString()
            val title = etTitle.text.toString()
            val lec = etLec.text.toString().toIntOrNull() ?: 0
            val lab = etLab.text.toString().toIntOrNull() ?: 0
            val units = etUnits.text.toString().toIntOrNull() ?: 0
            
            val year = selectedYearLevel ?: 1
            val sem = selectedSemester ?: 1

            if (code.isNotEmpty() && title.isNotEmpty() && selectedCurriculumId != null) {
                saveCourse(code, title, lec, lab, units, year, sem, dialog)
            } else {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun saveCourse(code: String, title: String, lec: Int, lab: Int, units: Int, year: Int, sem: Int, dialog: Dialog) {
        lifecycleScope.launch {
            try {
                val courseRequest = CourseRequest(selectedCurriculumId!!, code, title, lab, lec, units, year, sem)
                RetrofitClient.apiService.addCourse(courseRequest)
                Toast.makeText(this@MainActivity, "Course added successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadCoursesAndPopulateLevels()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to add course", Toast.LENGTH_SHORT).show()
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun showEditCourseDialog(course: CourseResponse) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_course)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<TextView>(R.id.tv_header).text = "Edit Course"
        val etCode = dialog.findViewById<EditText>(R.id.et_course_code)
        val etTitle = dialog.findViewById<EditText>(R.id.et_descriptive_title)
        val etLec = dialog.findViewById<EditText>(R.id.et_lec_hours)
        val etLab = dialog.findViewById<EditText>(R.id.et_lab_hours)
        val etUnits = dialog.findViewById<EditText>(R.id.et_credit_hours)
        val btnSave = dialog.findViewById<Button>(R.id.btn_add)
        btnSave.text = "Update"
        
        etCode.setText(course.course_code)
        etTitle.setText(course.descriptive_title)
        etLec.setText(course.lecture_hours.toString())
        etLab.setText(course.laboratory_hours.toString())
        etUnits.setText(course.credit_units.toString())

        btnSave.setOnClickListener {
            val code = etCode.text.toString()
            val title = etTitle.text.toString()
            val lec = etLec.text.toString().toIntOrNull() ?: 0
            val lab = etLab.text.toString().toIntOrNull() ?: 0
            val units = etUnits.text.toString().toIntOrNull() ?: 0
            
            if (code.isNotEmpty() && title.isNotEmpty()) {
                updateCourse(course.id, code, title, lec, lab, units, course.year_level, course.semester, dialog)
            }
        }
        dialog.findViewById<Button>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateCourse(id: Int, code: String, title: String, lec: Int, lab: Int, units: Int, year: Int, sem: Int, dialog: Dialog) {
        lifecycleScope.launch {
            try {
                val courseRequest = CourseRequest(selectedCurriculumId!!, code, title, lab, lec, units, year, sem)
                RetrofitClient.apiService.updateCourse(id, courseRequest)
                Toast.makeText(this@MainActivity, "Course updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadCourses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to update course", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun showDeleteCourseConfirmation(course: CourseResponse) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Course")
        builder.setMessage("Are you sure you want to delete ${course.course_code}?")
        builder.setPositiveButton("Delete") { _, _ ->
            deleteCourse(course.id)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun deleteCourse(id: Int) {
        lifecycleScope.launch {
            try {
                RetrofitClient.apiService.deleteCourse(id)
                Toast.makeText(this@MainActivity, "Course deleted", Toast.LENGTH_SHORT).show()
                loadCourses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to delete course", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
            }
        }
    }

    private fun updateNavUI(nav: String) {
        if (currentNav == nav) return
        
        // Trigger animations for the navigation anchor and the main scrollable content area
        TransitionManager.beginDelayedTransition(binding.navAnchor, AutoTransition())
        (binding.scrollContent.getChildAt(0) as? ViewGroup)?.let {
            TransitionManager.beginDelayedTransition(it, AutoTransition())
        }

        currentNav = nav
        
        val email = currentUserFaculty?.email?.lowercase()?.trim() ?: ""
        val isStaff = email == "mpmariano@tip.edu.ph"

        // Reset all
        resetNavItem(binding.navHome, binding.tvHome, binding.ivHome)
        resetNavItem(binding.navCourse, binding.tvCourse, binding.ivCourse)
        resetNavItem(binding.navSchedule, binding.tvSchedule, binding.ivSchedule)
        resetNavItem(binding.navProfile, binding.tvProfile, binding.ivProfile)
        
        val staffHome = findViewById<LinearLayout>(R.id.nav_staff_home)
        val staffSchedule = findViewById<LinearLayout>(R.id.nav_staff_schedule)
        val staffProfile = findViewById<LinearLayout>(R.id.nav_staff_profile)
        
        if (staffHome != null) {
            resetStaffNavItem(staffHome, findViewById(R.id.tv_staff_home), findViewById(R.id.iv_staff_home))
            resetStaffNavItem(staffSchedule, findViewById(R.id.tv_staff_schedule), findViewById(R.id.iv_staff_schedule))
            resetStaffNavItem(staffProfile, findViewById(R.id.tv_staff_profile), findViewById(R.id.iv_staff_profile))
        }

        val activeBg = ResourcesCompat.getDrawable(resources, R.drawable.bg_active_nav, theme)
        val activeColor = Color.parseColor("#1E2124")

        when (nav) {
            "home" -> {
                if (isStaff) {
                    staffHome?.let { setActiveStaffNavItem(it, findViewById(R.id.tv_staff_home), findViewById(R.id.iv_staff_home), activeBg, activeColor) }
                } else {
                    setActiveNavItem(binding.navHome, binding.tvHome, binding.ivHome, activeBg, activeColor)
                }
                binding.layoutHome.visibility = View.VISIBLE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
                binding.layoutStaffSchedule.root.visibility = View.GONE
                binding.layoutStaffProfile.root.visibility = View.GONE
            }
            "course" -> {
                setActiveNavItem(binding.navCourse, binding.tvCourse, binding.ivCourse, activeBg, activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.VISIBLE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
                binding.layoutStaffSchedule.root.visibility = View.GONE
                binding.layoutStaffProfile.root.visibility = View.GONE
                loadCourses()
            }
            "schedule" -> {
                if (isStaff) {
                    staffSchedule?.let { setActiveStaffNavItem(it, findViewById(R.id.tv_staff_schedule), findViewById(R.id.iv_staff_schedule), activeBg, activeColor) }
                    binding.layoutStaffSchedule.root.visibility = View.VISIBLE
                    binding.layoutSchedule.root.visibility = View.GONE
                } else {
                    setActiveNavItem(binding.navSchedule, binding.tvSchedule, binding.ivSchedule, activeBg, activeColor)
                    binding.layoutSchedule.root.visibility = View.VISIBLE
                    binding.layoutStaffSchedule.root.visibility = View.GONE
                }
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
                binding.layoutStaffProfile.root.visibility = View.GONE
                loadMySchedule(isAdmin = !isStaff)
            }
            "profile" -> {
                if (isStaff) {
                    staffProfile?.let { setActiveStaffNavItem(it, findViewById(R.id.tv_staff_profile), findViewById(R.id.iv_staff_profile), activeBg, activeColor) }
                    binding.layoutStaffProfile.root.visibility = View.VISIBLE
                    binding.layoutProfile.root.visibility = View.GONE
                } else {
                    setActiveNavItem(binding.navProfile, binding.tvProfile, binding.ivProfile, activeBg, activeColor)
                    binding.layoutProfile.root.visibility = View.VISIBLE
                    binding.layoutStaffProfile.root.visibility = View.GONE
                }
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutStaffSchedule.root.visibility = View.GONE
            }
        }
    }

    private fun resetNavItem(layout: LinearLayout, textView: TextView, imageView: ImageView) {
        layout.background = null
        textView.visibility = View.GONE
        imageView.setColorFilter(Color.WHITE)
        val params = layout.layoutParams as LinearLayout.LayoutParams
        params.weight = 15f
        layout.layoutParams = params
    }

    private fun setActiveNavItem(layout: LinearLayout, textView: TextView, imageView: ImageView, activeBg: android.graphics.drawable.Drawable?, activeColor: Int) {
        layout.background = activeBg
        textView.visibility = View.VISIBLE
        imageView.setColorFilter(activeColor)
        val params = layout.layoutParams as LinearLayout.LayoutParams
        params.weight = 55f
        layout.layoutParams = params
    }
    
    private fun resetStaffNavItem(layout: LinearLayout, textView: TextView, imageView: ImageView) {
        layout.background = null
        textView.visibility = View.GONE
        imageView.setColorFilter(Color.WHITE)
        val params = layout.layoutParams as LinearLayout.LayoutParams
        params.weight = 20f
        layout.layoutParams = params
    }

    private fun setActiveStaffNavItem(layout: LinearLayout, textView: TextView, imageView: ImageView, activeBg: android.graphics.drawable.Drawable?, activeColor: Int) {
        layout.background = activeBg
        textView.visibility = View.VISIBLE
        imageView.setColorFilter(activeColor)
        val params = layout.layoutParams as LinearLayout.LayoutParams
        params.weight = 60f
        layout.layoutParams = params
    }

    private fun loadRecentActivities() {
        val container = binding.llRecentActivityList
        container.removeAllViews()

        val activities = listOf(
            "Added Course: CS 101",
            "Created Schedule for BSIT 1-A",
            "Added new Staff: John Doe"
        )

        if (activities.isEmpty()) {
            binding.tvNoRecentActivity.visibility = View.VISIBLE
            binding.tvRecentActivityDateHeader.visibility = View.GONE
        } else {
            binding.tvNoRecentActivity.visibility = View.GONE
            binding.tvRecentActivityDateHeader.visibility = View.VISIBLE
            activities.forEach { activity ->
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_recent_activity, container, false)
                itemView.findViewById<TextView>(R.id.tv_activity_message).text = activity
                itemView.findViewById<TextView>(R.id.tv_activity_time).text = "Today"
                container.addView(itemView)
            }
        }
    }

    private fun setupPrinting() {
        binding.layoutSchedule.btnPrint.setOnClickListener { printSchedule() }
        binding.layoutSchedule.btnDownloadPdf.setOnClickListener { printSchedule() }
        
        binding.layoutStaffSchedule.btnStaffPrint.setOnClickListener { printSchedule() }
        binding.layoutStaffSchedule.btnStaffDownloadPdf.setOnClickListener { printSchedule() }
    }

    private fun printSchedule() {
        lifecycleScope.launch {
            try {
                val responseBody = RetrofitClient.apiService.getStaffScheduleHtml()
                val htmlContent = responseBody.string()
                runOnUiThread { doPrint(htmlContent) }
            } catch (e: Exception) {
                Log.e("MainActivity", "Print failed", e)
                if (e is HttpException && e.code() == 401) handleSessionFailure("Unauthorized", true)
                else Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doPrint(htmlContent: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                createWebPrintJob(view)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun createWebPrintJob(webView: WebView) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Schedule Print Job"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    private fun handleSessionFailure(message: String, isAuthError: Boolean = false) {
        if (isAuthFailureHandled) return
        if (isAuthError) isAuthFailureHandled = true
        runOnUiThread {
            val displayMessage = if (isAuthError) "Session expired. Please login again." else message
            Toast.makeText(this, displayMessage, Toast.LENGTH_LONG).show()
            RetrofitClient.logout()
            redirectToLogin()
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupProfileLogic() {
        // Shared Setup Logic
        setupProfileSpinners(binding.layoutProfile.spinnerGender, binding.layoutProfile.spinnerEmploymentStatus, binding.layoutProfile.spinnerDegree)
        setupProfileSpinners(binding.layoutStaffProfile.spinnerStaffGender, binding.layoutStaffProfile.spinnerStaffEmploymentStatus, binding.layoutStaffProfile.spinnerStaffDegree)

        // Admin Listeners
        binding.layoutProfile.btnEditProfile.setOnClickListener { toggleProfileEdit(true, isAdmin = true) }
        binding.layoutProfile.btnCancelEdit.setOnClickListener { 
            toggleProfileEdit(false, isAdmin = true)
            currentUserFaculty?.let { restoreProfileData(it, isAdmin = true) }
        }
        binding.layoutProfile.btnSaveProfile.setOnClickListener { saveProfileChanges(isAdmin = true) }
        binding.layoutProfile.btnLogout.setOnClickListener { logout() }

        // Staff Listeners
        binding.layoutStaffProfile.btnStaffEditProfile.setOnClickListener { toggleProfileEdit(true, isAdmin = false) }
        binding.layoutStaffProfile.btnStaffCancelEdit.setOnClickListener { 
            toggleProfileEdit(false, isAdmin = false)
            currentUserFaculty?.let { restoreProfileData(it, isAdmin = false) }
        }
        binding.layoutStaffProfile.btnStaffSaveProfile.setOnClickListener { saveProfileChanges(isAdmin = false) }
        binding.layoutStaffProfile.btnStaffLogout.setOnClickListener { logout() }

        // Mutual Exclusivity
        binding.layoutProfile.cbQualified.setOnCheckedChangeListener { _, isChecked -> if (isChecked) binding.layoutProfile.cbNa.isChecked = false }
        binding.layoutProfile.cbNa.setOnCheckedChangeListener { _, isChecked -> if (isChecked) binding.layoutProfile.cbQualified.isChecked = false }
        
        binding.layoutStaffProfile.cbStaffQualified.setOnCheckedChangeListener { _, isChecked -> if (isChecked) binding.layoutStaffProfile.cbStaffNa.isChecked = false }
        binding.layoutStaffProfile.cbStaffNa.setOnCheckedChangeListener { _, isChecked -> if (isChecked) binding.layoutStaffProfile.cbStaffQualified.isChecked = false }

        // Visibility Toggles
        setupPasswordToggle(binding.layoutProfile.ivShowPassword, binding.layoutProfile.etPassword)
        setupPasswordToggle(binding.layoutProfile.ivShowNewPassword, binding.layoutProfile.etNewPassword)
        setupPasswordToggle(binding.layoutStaffProfile.ivStaffShowPassword, binding.layoutStaffProfile.etStaffPassword)
        setupPasswordToggle(binding.layoutStaffProfile.ivStaffShowNewPassword, binding.layoutStaffProfile.etStaffNewPassword)

        toggleProfileEdit(false, isAdmin = true)
        toggleProfileEdit(false, isAdmin = false)
    }

    private fun setupProfileSpinners(gender: Spinner, emp: Spinner, deg: Spinner) {
        gender.adapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, listOf("Male", "Female")).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
        emp.adapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, listOf("Full-Time", "Part-Time", "Contractual")).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
        deg.adapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, listOf("Master's Degree", "Doctoral's Degree")).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
    }

    private fun setupPasswordToggle(icon: ImageView, editText: EditText) {
        var isVisible = false
        icon.setOnClickListener {
            isVisible = !isVisible
            editText.transformationMethod = if (isVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            icon.setColorFilter(if (isVisible) Color.parseColor("#1E2124") else Color.parseColor("#888888"))
            editText.setSelection(editText.text.length)
        }
    }

    private fun toggleProfileEdit(enabled: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            val l = binding.layoutProfile
            l.etFirstName.isEnabled = enabled
            l.etLastName.isEnabled = enabled
            l.spinnerGender.isEnabled = enabled
            l.etPassword.isEnabled = enabled
            l.etNewPassword.isEnabled = enabled
            l.llChangePasswordContainer.visibility = if (enabled) View.VISIBLE else View.GONE
            l.spinnerEmploymentStatus.isEnabled = enabled
            l.spinnerDegree.isEnabled = enabled
            l.spinnerSpecialization.isEnabled = enabled
            l.cbQualified.isEnabled = enabled
            l.cbNa.isEnabled = enabled
            l.llEditActions.visibility = if (enabled) View.VISIBLE else View.GONE
            l.btnEditProfile.visibility = if (enabled) View.GONE else View.VISIBLE
        } else {
            val l = binding.layoutStaffProfile
            l.etStaffFirstName.isEnabled = enabled
            l.etStaffLastName.isEnabled = enabled
            l.spinnerStaffGender.isEnabled = enabled
            l.etStaffPassword.isEnabled = enabled
            l.etStaffNewPassword.isEnabled = enabled
            l.llStaffChangePasswordContainer.visibility = if (enabled) View.VISIBLE else View.GONE
            
            // Employment Status, Educational Attainment and PRC License are ALWAYS read-only for staff
            l.spinnerStaffEmploymentStatus.isEnabled = false
            l.spinnerStaffDegree.isEnabled = false
            l.spinnerStaffSpecialization.isEnabled = false
            l.cbStaffQualified.isEnabled = false
            l.cbStaffNa.isEnabled = false

            l.llStaffEditActions.visibility = if (enabled) View.VISIBLE else View.GONE
            l.btnStaffEditProfile.visibility = if (enabled) View.GONE else View.VISIBLE
        }
    }

    private fun restoreProfileData(data: FacultyData, isAdmin: Boolean) {
        if (isAdmin) {
            binding.layoutProfile.etFirstName.setText(data.first_name)
            binding.layoutProfile.etLastName.setText(data.last_name)
            binding.layoutProfile.etPassword.setText("")
            binding.layoutProfile.etNewPassword.setText("")
        } else {
            binding.layoutStaffProfile.etStaffFirstName.setText(data.first_name)
            binding.layoutStaffProfile.etStaffLastName.setText(data.last_name)
            binding.layoutStaffProfile.etStaffPassword.setText("")
            binding.layoutStaffProfile.etStaffNewPassword.setText("")
        }
        updateSpecializationCheckboxes()
    }

    private fun logout() {
        RetrofitClient.logout()
        redirectToLogin()
    }

    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { "!@#$%^&*".contains(it) }) return false
        return true
    }

    private fun saveProfileChanges(isAdmin: Boolean) {
        val l = if (isAdmin) binding.layoutProfile else null
        val ls = if (!isAdmin) binding.layoutStaffProfile else null
        
        val newFirstName = l?.etFirstName?.text?.toString() ?: ls?.etStaffFirstName?.text?.toString() ?: ""
        val newLastName = l?.etLastName?.text?.toString() ?: ls?.etStaffLastName?.text?.toString() ?: ""
        
        val currentPassword = l?.etPassword?.text?.toString() ?: ls?.etStaffPassword?.text?.toString() ?: ""
        val newPassword = l?.etNewPassword?.text?.toString() ?: ls?.etStaffNewPassword?.text?.toString() ?: ""

        if (newPassword.isNotEmpty()) {
            if (currentPassword.isEmpty()) {
                Toast.makeText(this, "Please enter your Current Password to change it", Toast.LENGTH_SHORT).show()
                return
            }
            if (!validatePassword(newPassword)) {
                Toast.makeText(this, "New Password Must be: at least 8 characters, 1 uppercase letter (A-Z), 1 special character (!@#$%^&*)", Toast.LENGTH_LONG).show()
                return
            }
        }

        Toast.makeText(this, "Updating profile...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val updateData = mutableMapOf<String, Any>(
                    "first_name" to newFirstName,
                    "last_name" to newLastName,
                    "specialization" to selectedSpecIds.toList()
                )
                
                if (newPassword.isNotEmpty()) {
                    updateData["current_password"] = currentPassword
                    updateData["new_password"] = newPassword
                }

                val updatedFaculty = RetrofitClient.apiService.updateProfile(updateData)
                currentUserFaculty = updatedFaculty
                Toast.makeText(this@MainActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                toggleProfileEdit(false, isAdmin)
                updateUserDataUI(updatedFaculty)
                
                // Clear password fields
                l?.etPassword?.setText("")
                l?.etNewPassword?.setText("")
                ls?.etStaffPassword?.setText("")
                ls?.etStaffNewPassword?.setText("")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Update failed", e)
                val errorMsg = if (e is HttpException && e.code() == 400) "Invalid current password or update data" else "Update failed"
                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserDataUI(data: FacultyData) {
        binding.tvGreeting.text = "Hello, ${data.first_name}!"
        binding.layoutProfile.tvProfileName.text = "${data.first_name} ${data.last_name}"
        binding.layoutStaffProfile.tvStaffProfileName.text = "${data.first_name} ${data.last_name}"
    }
}
