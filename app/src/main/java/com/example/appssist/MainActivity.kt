package com.example.appssist

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.appssist.databinding.ActivityDashboardBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private var currentNav = "home"

    private var curriculums = listOf<CurriculumResponse>()
    private var sections = listOf<SectionResponse>()
    private var courses = listOf<CourseResponse>()
    
    // To store unique year/sem pairs for the dynamic spinner
    private var availableLevels = mutableListOf<Pair<Int, Int>>()

    private var selectedCurriculumId: Int? = null
    private var selectedYearLevel: Int? = null
    private var selectedSemester: Int? = null

    private var activeActionsView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fetchUserData()
        fetchDashboardData()

        setupQuickActions()
        setupBottomNavigation()
        setupCourseBackend()

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
    }

    private fun handleCourseAddClick() {
        if (selectedCurriculumId != null) {
            showAddCourseDialog()
        } else {
            Toast.makeText(this, "Please select a Curriculum first", Toast.LENGTH_SHORT).show()
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
        
        // Initial setup for the Level spinner (just "All Year Levels")
        resetAcademicLevelSpinner()
    }

    private fun hideActiveActions() {
        activeActionsView?.visibility = View.GONE
        activeActionsView = null
    }

    private fun fetchUserData() {
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null)

        if (accessToken.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val authHeader = "Bearer $accessToken"
                val data = RetrofitClient.apiService.getUserFacultyData(authHeader)

                val greeting = if (data.first_name.isNotEmpty()) {
                    "Hello, ${data.first_name}!"
                } else {
                    "Hello!"
                }
                binding.tvGreeting.text = greeting
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("MainActivity", "HTTP Error ${e.code()}: $errorBody")
                handleSessionFailure("Server Error: ${e.code()}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Data Error: ${e.message}", e)
                handleSessionFailure("Data Error: ${e.message}")
            }
        }
    }

    private fun fetchDashboardData() {
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                val stats = RetrofitClient.apiService.getDashboardStats(authHeader)
                binding.tvTotalStaff.text = stats.faculty_count.toString()
                binding.tvTotalSections.text = stats.section_count.toString()

                curriculums = RetrofitClient.apiService.getCurriculums(authHeader)
                updateCurriculumSpinner()

                sections = RetrofitClient.apiService.getSections(authHeader)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to fetch dashboard data", e)
            }
        }
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
                    selectedYearLevel = null // Reset level filter on curriculum change
                    selectedSemester = null
                    // Fetch all courses for this curriculum to see what levels exist
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                // Fetch ALL courses for the selected curriculum first
                val allCourses = RetrofitClient.apiService.getCourses(authHeader, selectedCurriculumId, null, null)
                courses = allCourses
                
                // Extract unique year/sem pairs
                availableLevels = allCourses.map { Pair(it.year_level, it.semester) }.distinct().sortedWith(compareBy({ it.first }, { it.second })).toMutableList()
                
                // Update the level spinner with only existing levels
                val levelNames = mutableListOf("All Year Levels")
                availableLevels.forEach { (year, sem) ->
                    val yearStr = when(year) {
                        1 -> "1st"
                        2 -> "2nd"
                        3 -> "3rd"
                        else -> "4th"
                    }
                    val semStr = if (sem == 1) "1st" else "2nd"
                    levelNames.add("$yearStr Year, $semStr Sem")
                }

                val levelAdapter = ArrayAdapter(this@MainActivity, R.layout.item_spinner_selected, android.R.id.text1, levelNames)
                levelAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                binding.layoutCourse.spinnerAcademicLevel.adapter = levelAdapter

                binding.layoutCourse.spinnerAcademicLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position > 0) {
                            val level = availableLevels[position - 1]
                            selectedYearLevel = level.first
                            selectedSemester = level.second
                        } else {
                            selectedYearLevel = null
                            selectedSemester = null
                        }
                        loadCourses()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                
                renderCourses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load courses and levels", e)
            }
        }
    }

    private fun loadCourses() {
        if (selectedCurriculumId == null) {
            courses = emptyList()
            renderCourses()
            return
        }

        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                courses = RetrofitClient.apiService.getCourses(authHeader, selectedCurriculumId, selectedYearLevel, selectedSemester)
                renderCourses()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load courses", e)
            }
        }
    }

    private fun renderCourses() {
        val container = binding.layoutCourse.llCourseList
        container.removeAllViews()

        if (selectedCurriculumId == null) {
            binding.layoutCourse.tvEmptyState.text = "Select or add a curriculum or academic term."
            binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
            binding.layoutCourse.llDynamicContent.visibility = View.GONE
            return
        }

        if (courses.isEmpty()) {
            binding.layoutCourse.tvEmptyState.text = "No courses found for this selection."
            binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
            binding.layoutCourse.llDynamicContent.visibility = View.GONE
            return
        }

        binding.layoutCourse.tvEmptyState.visibility = View.GONE
        binding.layoutCourse.llDynamicContent.visibility = View.VISIBLE

        if (selectedYearLevel != null && selectedSemester != null) {
            val yearStr = when(selectedYearLevel) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "4th"
            }
            val semStr = if (selectedSemester == 1) "1st" else "2nd"
            val totalUnits = courses.sumOf { it.credit_units }
            binding.layoutCourse.tvSemesterHeader.text = "$yearStr Year, $semStr Semester ($totalUnits Units)"
        } else {
            binding.layoutCourse.tvSemesterHeader.text = "All Year Levels (${courses.sumOf { it.credit_units }} Units)"
        }

        courses.forEach { course ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_course, container, false)
            itemView.findViewById<TextView>(R.id.tv_course_code).text = course.course_code
            itemView.findViewById<TextView>(R.id.tv_course_title).text = course.descriptive_title
            
            val details = "${course.lecture_hours} Lec, ${course.laboratory_hours} Lab | ${course.credit_units} Units"
            itemView.findViewById<TextView>(R.id.tv_course_details).text = details
            
            container.addView(itemView)
        }
    }

    private fun handleSessionFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Remaining placeholders
    private fun showAddCourseDialog() {}
    private fun loadRecentActivities() {}

    private fun updateNavUI(selected: String) {
        if (selected == currentNav) return
        val transition = AutoTransition()
        transition.duration = 250
        TransitionManager.beginDelayedTransition(binding.root, transition)

        val navMap = mapOf(
            "home" to Triple(binding.navHome, binding.ivHome, binding.tvHome),
            "course" to Triple(binding.navCourse, binding.ivCourse, binding.tvCourse),
            "schedule" to Triple(binding.navSchedule, binding.ivSchedule, binding.tvSchedule),
            "profile" to Triple(binding.navProfile, binding.ivProfile, binding.tvProfile)
        )
        val layoutMap = mapOf(
            "home" to binding.layoutHome, 
            "course" to binding.layoutCourse.root, 
            "schedule" to binding.layoutSchedule.root, 
            "profile" to binding.layoutProfile.root
        )

        navMap.forEach { (key, views) ->
            val (container, icon, text) = views
            val params = container.layoutParams as LinearLayout.LayoutParams
            if (key == selected) {
                params.weight = 40f
                container.setBackgroundResource(R.drawable.bg_active_nav)
                icon.setColorFilter(Color.parseColor("#1E2124"))
                text.visibility = View.VISIBLE
                layoutMap[key]?.visibility = View.VISIBLE
            } else {
                params.weight = 20f
                container.background = null
                icon.setColorFilter(Color.WHITE)
                text.visibility = View.GONE
                layoutMap[key]?.visibility = View.GONE
            }
            container.layoutParams = params
        }
        currentNav = selected
        binding.scrollContent.smoothScrollTo(0, 0)
    }
}
