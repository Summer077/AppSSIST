package com.example.appssist

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        RetrofitClient.init(this)
        
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
    }

    private fun setupCourseBackend() {
        binding.layoutCourse.btnAddCourseTop.setOnClickListener {
            handleCourseAddClick()
        }
        resetAcademicLevelSpinner()
    }

    private fun setupScheduleBackend() {
        // Tab Listeners
        val tabs = listOf(
            binding.layoutSchedule.tabMySchedule,
            binding.layoutSchedule.tabFaculty,
            binding.layoutSchedule.tabSection,
            binding.layoutSchedule.tabRoom
        )

        tabs.forEach { tab ->
            tab.setBackgroundResource(R.drawable.bg_neumorphic_tab)
            tab.setOnClickListener {
                updateScheduleTabsUI(tab, tabs)
                
                // Switch between sections
                when(tab.id) {
                    R.id.tab_my_schedule -> {
                        binding.layoutSchedule.llMyScheduleSection.visibility = View.VISIBLE
                        binding.layoutSchedule.llFacultySection.visibility = View.GONE
                        binding.layoutSchedule.llSectionSection.visibility = View.GONE
                        binding.layoutSchedule.llRoomSection.visibility = View.GONE
                        loadMySchedule()
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
        // Default select first tab
        binding.layoutSchedule.tabMySchedule.performClick()

        // Day Selector Listeners
        val days = listOf(
            binding.layoutSchedule.btnDayMon,
            binding.layoutSchedule.btnDayTue,
            binding.layoutSchedule.btnDayWed,
            binding.layoutSchedule.btnDayThurs,
            binding.layoutSchedule.btnDayFri,
            binding.layoutSchedule.btnDaySat
        )

        days.forEach { dayView ->
            dayView.setOnClickListener {
                days.forEach { it.isSelected = false }
                dayView.isSelected = true
                
                val dayName = when(dayView.id) {
                    R.id.btn_day_mon -> "Monday"
                    R.id.btn_day_tue -> "Tuesday"
                    R.id.btn_day_wed -> "Wednesday"
                    R.id.btn_day_thurs -> "Thursday"
                    R.id.btn_day_fri -> "Friday"
                    R.id.btn_day_sat -> "Saturday"
                    else -> ""
                }
                displayScheduleForDay(dayName)
            }
        }
        
        // Default select Monday
        binding.layoutSchedule.btnDayMon.performClick()
    }

    private fun loadMySchedule() {
        val facultyId = currentUserFaculty?.id ?: return
        if (facultyId <= 0) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getFacultySchedule(facultyId)
                mySchedules = response.results ?: emptyList()
                updateDayDots()
                val selectedDay = when {
                    binding.layoutSchedule.btnDayMon.isSelected -> "Monday"
                    binding.layoutSchedule.btnDayTue.isSelected -> "Tuesday"
                    binding.layoutSchedule.btnDayWed.isSelected -> "Wednesday"
                    binding.layoutSchedule.btnDayThurs.isSelected -> "Thursday"
                    binding.layoutSchedule.btnDayFri.isSelected -> "Friday"
                    binding.layoutSchedule.btnDaySat.isSelected -> "Saturday"
                    else -> "Monday"
                }
                displayScheduleForDay(selectedDay)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load my schedule", e)
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

    private fun updateDayDots() {
        val dayViews = mapOf(
            "Monday" to binding.layoutSchedule.btnDayMon,
            "Tuesday" to binding.layoutSchedule.btnDayTue,
            "Wednesday" to binding.layoutSchedule.btnDayWed,
            "Thursday" to binding.layoutSchedule.btnDayThurs,
            "Friday" to binding.layoutSchedule.btnDayFri,
            "Saturday" to binding.layoutSchedule.btnDaySat
        )

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

    private fun displayScheduleForDay(dayName: String) {
        val gridContainer = binding.layoutSchedule.llTimetableGrid
        val cardsContainer = binding.layoutSchedule.flTimetableCards
        gridContainer.removeAllViews()
        cardsContainer.removeAllViews()

        val daySchedules = mySchedules.filter { getDayStringFromInt(it.day).equals(dayName, ignoreCase = true) }

        if (daySchedules.isEmpty()) {
            binding.layoutSchedule.llNoScheduleState.visibility = View.VISIBLE
            binding.layoutSchedule.rlTimetableContainer.visibility = View.GONE
        } else {
            binding.layoutSchedule.llNoScheduleState.visibility = View.GONE
            binding.layoutSchedule.rlTimetableContainer.visibility = View.VISIBLE
            
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
                val facultySchedules = when(type) {
                    "faculty" -> RetrofitClient.apiService.getFacultySchedule(resourceId).results ?: emptyList()
                    "section" -> RetrofitClient.apiService.getSectionSchedule(resourceId).results ?: emptyList()
                    "room" -> RetrofitClient.apiService.getRoomSchedule(resourceId).results ?: emptyList()
                    else -> emptyList()
                }

                days.forEachIndexed { index, dayName ->
                    val dayView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_day_schedule_card, daysContainer, false)
                    dayView.findViewById<TextView>(R.id.tv_day_name).text = dayName
                    
                    val dotsContainer = dayView.findViewById<LinearLayout>(R.id.ll_dots_container)
                    val daySchedules = facultySchedules.filter { getDayStringFromInt(it.day).equals(fullDays[index], ignoreCase = true) }
                    
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
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Print Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null)

        if (accessToken.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                var data = RetrofitClient.apiService.getUserFacultyData()
                
                if (data.id <= 0) {
                    val facultyList = RetrofitClient.apiService.getFacultyList()
                    val matchingFaculty = facultyList.find { it.email?.trim()?.equals(data.email.trim(), ignoreCase = true) == true }
                    if (matchingFaculty != null) {
                        data = data.copy(id = matchingFaculty.id)
                    }
                }
                
                currentUserFaculty = data

                val greeting = if (data.first_name.isNotEmpty()) {
                    "Hello, ${data.first_name}!"
                } else {
                    "Hello!"
                }
                binding.tvGreeting.text = greeting
                
                // Update profile UI with data
                binding.layoutProfile.tvProfileName.text = "${data.first_name} ${data.last_name}"
                binding.layoutProfile.tvProfileEmailSub.text = data.email
                binding.layoutProfile.etFirstName.setText(data.first_name)
                binding.layoutProfile.etLastName.setText(data.last_name)
                binding.layoutProfile.etEmail.setText(data.email)
                
                // Set initial selection for gender spinner
                val genderIndex = if (data.gender?.equals("F", ignoreCase = true) == true || data.gender?.equals("Female", ignoreCase = true) == true) 1 else 0
                binding.layoutProfile.spinnerGender.setSelection(genderIndex)

                // Set initial selection for employment status spinner
                val employmentOptions = listOf("Full-Time", "Part-Time", "Contractual")
                val empValue = when(data.employment_status?.lowercase()) {
                    "full_time" -> "Full-Time"
                    "part_time" -> "Part-Time"
                    "contractual" -> "Contractual"
                    else -> "Full-Time"
                }
                val empIndex = employmentOptions.indexOfFirst { it.equals(empValue, ignoreCase = true) }.let { if (it == -1) 0 else it }
                binding.layoutProfile.spinnerEmploymentStatus.setSelection(empIndex)
                
                // Set initial selection for degree spinner
                val degreeOptions = listOf("Master's Degree", "Doctoral's Degree")
                val degIndex = when {
                    data.highest_degree?.contains("Master", ignoreCase = true) == true -> 0
                    data.highest_degree?.contains("Doctor", ignoreCase = true) == true -> 1
                    else -> 0
                }
                binding.layoutProfile.spinnerDegree.setSelection(degIndex)
                
                loadMySchedule()
            } catch (e: HttpException) {
                handleSessionFailure("Server Error: ${e.code()}")
            } catch (e: Exception) {
                handleSessionFailure("Data Error: ${e.message}")
            }
        }
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
            }
        }
    }

    private fun updateNavUI(nav: String) {
        if (currentNav == nav) return
        currentNav = nav

        // Reset all backgrounds, visibility and weights
        resetNavItem(binding.navHome, binding.tvHome, binding.ivHome)
        resetNavItem(binding.navCourse, binding.tvCourse, binding.ivCourse)
        resetNavItem(binding.navSchedule, binding.tvSchedule, binding.ivSchedule)
        resetNavItem(binding.navProfile, binding.tvProfile, binding.ivProfile)

        // Set active state
        val activeBg = ResourcesCompat.getDrawable(resources, R.drawable.bg_active_nav, theme)
        val activeColor = Color.parseColor("#1E2124")

        TransitionManager.beginDelayedTransition(binding.bottomNavContainer, AutoTransition())

        when (nav) {
            "home" -> {
                setActiveNavItem(binding.navHome, binding.tvHome, binding.ivHome, activeBg, activeColor)
                binding.layoutHome.visibility = View.VISIBLE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
            }
            "course" -> {
                setActiveNavItem(binding.navCourse, binding.tvCourse, binding.ivCourse, activeBg, activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.VISIBLE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
                loadCourses()
            }
            "schedule" -> {
                setActiveNavItem(binding.navSchedule, binding.tvSchedule, binding.ivSchedule, activeBg, activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.VISIBLE
                binding.layoutProfile.root.visibility = View.GONE
            }
            "profile" -> {
                setActiveNavItem(binding.navProfile, binding.tvProfile, binding.ivProfile, activeBg, activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.VISIBLE
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
        binding.layoutSchedule.btnPrint.setOnClickListener {
            printSchedule()
        }
        binding.layoutSchedule.btnDownloadPdf.setOnClickListener {
            printSchedule() // In Android, Save as PDF is part of the standard print flow
        }
    }

    private fun printSchedule() {
        lifecycleScope.launch {
            try {
                // Determine which HTML to fetch based on active schedule tab or current user
                // Defaulting to staff (my schedule) version
                val responseBody = RetrofitClient.apiService.getStaffScheduleHtml()
                val htmlContent = responseBody.string()
                
                runOnUiThread {
                    doPrint(htmlContent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Print failed", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
        // Use a base URL if you have local assets like images/CSS, otherwise null is fine
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun createWebPrintJob(webView: WebView) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Schedule Print Job"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        
        printManager.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
    }

    private fun handleSessionFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setupProfileLogic() {
        val layout = binding.layoutProfile
        
        // Setup Gender Spinner with custom design
        val genderOptions = listOf("Male", "Female")
        val genderAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, genderOptions)
        genderAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        layout.spinnerGender.adapter = genderAdapter
        
        // Setup Employment Status Spinner with custom design
        val employmentOptions = listOf("Full-Time", "Part-Time", "Contractual")
        val empAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, employmentOptions)
        empAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        layout.spinnerEmploymentStatus.adapter = empAdapter

        // Setup Highest Degree Spinner with custom design
        val degreeOptions = listOf("Master's Degree", "Doctoral's Degree")
        val degAdapter = ArrayAdapter(this, R.layout.item_spinner_selected, android.R.id.text1, degreeOptions)
        degAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        layout.spinnerDegree.adapter = degAdapter

        layout.btnEditProfile.setOnClickListener {
            toggleProfileEdit(true)
        }
        
        layout.ivProfileBg.setOnClickListener {
            toggleProfileEdit(true)
        }
        
        layout.cvProfilePic.setOnClickListener {
            toggleProfileEdit(true)
        }

        // Removed unlock email listener as it's now read-only
        
        layout.btnCancelEdit.setOnClickListener {
            toggleProfileEdit(false)
            // Restore original values
            currentUserFaculty?.let { data ->
                layout.etFirstName.setText(data.first_name)
                layout.etLastName.setText(data.last_name)
                layout.etEmail.setText(data.email)
                
                val genderIndex = if (data.gender?.equals("F", ignoreCase = true) == true || data.gender?.equals("Female", ignoreCase = true) == true) 1 else 0
                layout.spinnerGender.setSelection(genderIndex)
                
                val empValue = when(data.employment_status?.lowercase()) {
                    "full_time" -> "Full-Time"
                    "part_time" -> "Part-Time"
                    "contractual" -> "Contractual"
                    else -> data.employment_status ?: ""
                }
                val empIndex = employmentOptions.indexOfFirst { it.equals(empValue, ignoreCase = true) }.let { if (it == -1) 0 else it }
                layout.spinnerEmploymentStatus.setSelection(empIndex)

                val degIndex = when {
                    data.highest_degree?.contains("Master", ignoreCase = true) == true -> 0
                    data.highest_degree?.contains("Doctor", ignoreCase = true) == true -> 1
                    else -> 0
                }
                layout.spinnerDegree.setSelection(degIndex)
            }
        }
        
        layout.btnSaveProfile.setOnClickListener {
            saveProfileChanges()
        }
        
        // Initialize in read-only mode
        toggleProfileEdit(false)

        layout.btnLogout.setOnClickListener {
            val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun toggleProfileEdit(enabled: Boolean) {
        val layout = binding.layoutProfile
        
        layout.etFirstName.isEnabled = enabled
        layout.etLastName.isEnabled = enabled
        
        // Email logic: Allow editing if enabled to fix sync issues
        layout.btnUnlockEmail.visibility = View.GONE
        layout.etEmail.isEnabled = enabled 
        if (enabled) {
            layout.etEmail.setTextColor(Color.BLACK)
            layout.tvEmailLabel.text = "Email"
        } else {
            layout.etEmail.setTextColor(Color.parseColor("#888888"))
            layout.tvEmailLabel.text = "Email (Read-only)"
        }
        
        layout.spinnerGender.isEnabled = enabled
        layout.etPassword.isEnabled = enabled
        layout.spinnerEmploymentStatus.isEnabled = enabled
        layout.spinnerDegree.isEnabled = enabled
        layout.etSpecialization.isEnabled = enabled
        layout.cbQualified.isEnabled = enabled
        layout.cbNa.isEnabled = enabled
        
        layout.llEditActions.visibility = if (enabled) View.VISIBLE else View.GONE
        layout.btnEditProfile.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun saveProfileChanges() {
        val layout = binding.layoutProfile
        val newFirstName = layout.etFirstName.text.toString().trim()
        val newLastName = layout.etLastName.text.toString().trim()
        val newEmail = layout.etEmail.text.toString().trim()
        
        // Convert display values back to backend-expected formats
        val displayGender = layout.spinnerGender.selectedItem.toString()
        val backendGender = if (displayGender == "Male") "M" else "F"
        
        val displayEmpStatus = layout.spinnerEmploymentStatus.selectedItem.toString()
        val backendEmpStatus = when(displayEmpStatus) {
            "Full-Time" -> "full_time"
            "Part-Time" -> "part_time"
            "Contractual" -> "contractual"
            else -> displayEmpStatus.lowercase().replace("-", "_")
        }
        
        val displayDegree = layout.spinnerDegree.selectedItem.toString()
        val backendDegree = when(displayDegree) {
            "Master's Degree" -> "Masters"
            "Doctoral's Degree" -> "Doctorate"
            else -> displayDegree
        }

        Toast.makeText(this, "Updating profile...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val updateData = mutableMapOf(
                    "first_name" to newFirstName,
                    "last_name" to newLastName,
                    "email" to newEmail,
                    "gender" to backendGender,
                    "employment_status" to backendEmpStatus,
                    "highest_degree" to backendDegree
                )
                
                val updatedFaculty = RetrofitClient.apiService.updateProfile(updateData)
                currentUserFaculty = updatedFaculty
                
                Toast.makeText(this@MainActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                
                layout.tvProfileName.text = "${updatedFaculty.first_name} ${updatedFaculty.last_name}"
                binding.tvGreeting.text = "Hello, ${updatedFaculty.first_name}!"
                
                // Update local fields with returned data to be sure
                layout.etEmail.setText(updatedFaculty.email)
                
                toggleProfileEdit(false)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to update profile", e)
                Toast.makeText(this@MainActivity, "Update failed. Server sync issue.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
