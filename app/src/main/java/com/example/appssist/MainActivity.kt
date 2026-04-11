package com.example.appssist

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
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
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private var currentNav = "home"

    private var curriculums = listOf<CurriculumResponse>()
    private var sections = listOf<SectionResponse>()
    private var courses = listOf<CourseResponse>()
    
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

        // Status message for feedback
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

        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null)
        if (accessToken == null) {
            tvStatus.text = "Error: No session found. Please login again."
            return
        }
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                val facultyList = RetrofitClient.apiService.getFacultyList(authHeader)
                container.removeAllViews()
                
                if (facultyList.isEmpty()) {
                    tvStatus.text = "No staff members found in database."
                    container.addView(tvStatus)
                } else {
                    facultyList.forEach { faculty ->
                        val itemView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_dashboard_list, container, false)
                        itemView.findViewById<TextView>(R.id.tv_item_name).apply {
                            text = "${faculty.first_name} ${faculty.last_name}"
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
                Log.e("MainActivity", "Failed to load faculty list", e)
                container.removeAllViews()
                val errorMsg = when(e) {
                    is HttpException -> "Server Error ${e.code()}: ${e.message()}"
                    is java.net.ConnectException -> "Connection failed. Check if server is running."
                    else -> "Error: ${e.localizedMessage ?: e.toString()}"
                }
                tvStatus.text = errorMsg
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                val allCourses = RetrofitClient.apiService.getCourses(authHeader, selectedCurriculumId, null, null)
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                val fetchedCourses = RetrofitClient.apiService.getCourses(authHeader, selectedCurriculumId, selectedYearLevel, selectedSemester)
                courses = fetchedCourses
                
                val container = binding.layoutCourse.llCourseList
                container.removeAllViews()

                if (courses.isEmpty()) {
                    binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.layoutCourse.tvEmptyState.visibility = View.GONE
                    courses.forEach { course ->
                        val itemView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_course, container, false)
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
        val btnSave = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                val courseRequest = CourseRequest(selectedCurriculumId!!, code, title, lab, lec, units, year, sem)
                RetrofitClient.apiService.addCourse(authHeader, courseRequest)
                Toast.makeText(this@MainActivity, "Course added successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadCoursesAndPopulateLevels()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to add course", e)
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                val courseRequest = CourseRequest(selectedCurriculumId!!, code, title, lab, lec, units, year, sem)
                RetrofitClient.apiService.updateCourse(id, authHeader, courseRequest)
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
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null) ?: return
        val authHeader = "Bearer $accessToken"

        lifecycleScope.launch {
            try {
                RetrofitClient.apiService.deleteCourse(id, authHeader)
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

        // Reset all backgrounds and visibility
        binding.navHome.background = null
        binding.tvHome.visibility = View.GONE
        binding.ivHome.setColorFilter(Color.WHITE)

        binding.navCourse.background = null
        binding.tvCourse.visibility = View.GONE
        binding.ivCourse.setColorFilter(Color.WHITE)

        binding.navSchedule.background = null
        binding.tvSchedule.visibility = View.GONE
        binding.ivSchedule.setColorFilter(Color.WHITE)

        binding.navProfile.background = null
        binding.tvProfile.visibility = View.GONE
        binding.ivProfile.setColorFilter(Color.WHITE)

        // Set active state
        val activeBg = ResourcesCompat.getDrawable(resources, R.drawable.bg_active_nav, theme)
        val activeColor = Color.parseColor("#1E2124")

        TransitionManager.beginDelayedTransition(binding.bottomNavContainer, AutoTransition())

        when (nav) {
            "home" -> {
                binding.navHome.background = activeBg
                binding.tvHome.visibility = View.VISIBLE
                binding.ivHome.setColorFilter(activeColor)
                binding.layoutHome.visibility = View.VISIBLE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
            }
            "course" -> {
                binding.navCourse.background = activeBg
                binding.tvCourse.visibility = View.VISIBLE
                binding.ivCourse.setColorFilter(activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.VISIBLE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.GONE
            }
            "schedule" -> {
                binding.navSchedule.background = activeBg
                binding.tvSchedule.visibility = View.VISIBLE
                binding.ivSchedule.setColorFilter(activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.VISIBLE
                binding.layoutProfile.root.visibility = View.GONE
            }
            "profile" -> {
                binding.navProfile.background = activeBg
                binding.tvProfile.visibility = View.VISIBLE
                binding.ivProfile.setColorFilter(activeColor)
                binding.layoutHome.visibility = View.GONE
                binding.layoutCourse.root.visibility = View.GONE
                binding.layoutSchedule.root.visibility = View.GONE
                binding.layoutProfile.root.visibility = View.VISIBLE
            }
        }
    }

    private fun loadRecentActivities() {
        val container = binding.llRecentActivityList
        container.removeAllViews()

        // Original bullet design - NO CARDS
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
                val tv = TextView(this)
                tv.text = "• $activity"
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                tv.setTextColor(Color.parseColor("#555555"))
                tv.setPadding(0, 4, 0, 4)
                tv.typeface = ResourcesCompat.getFont(this, R.font.lexend)
                container.addView(tv)
            }
        }
    }

    private fun handleSessionFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
