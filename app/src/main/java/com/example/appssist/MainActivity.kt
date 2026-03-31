package com.example.appssist

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.appssist.databinding.ActivityDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var currentNav = "home"

    private var curriculumList = mutableListOf<String>()
    private var academicLevelList = mutableListOf<String>()
    
    private var selectedCurriculum: String? = null
    private var selectedAcademicLevel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fetchUserData()

        // Stat counters
        binding.tvTotalStaff.text = "0"
        binding.tvTotalSections.text = "0"

        setupQuickActions()
        setupBottomNavigation()
        setupCourseBackend()
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
        if (selectedCurriculum != null && selectedAcademicLevel != null) {
            showAddCourseDialog()
        } else {
            Toast.makeText(this, "Please select Curriculum and Academic Level first", Toast.LENGTH_SHORT).show()
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
        
        loadCurriculums()
    }

    private fun loadCurriculums() {
        db.collection("curriculums").orderBy("year").get().addOnSuccessListener { documents ->
            curriculumList.clear()
            for (doc in documents) {
                curriculumList.add(doc.id)
            }
            updateCurriculumSpinner()
        }
    }

    private fun updateCurriculumSpinner() {
        val options = mutableListOf<String>()
        options.add("Curriculum") // Hint
        curriculumList.forEach { options.add("$it Curriculum") }
        options.add("+ Add Curriculum")
        
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 11f
                view.setTextColor(if (position == 0) Color.parseColor("#888888") else Color.parseColor("#1E2124"))
                view.textStyle = android.graphics.Typeface.BOLD
                try {
                    view.typeface = android.graphics.Typeface.createFromAsset(assets, "font/lexend.ttf")
                } catch (e: Exception) {}
                view.setPadding(0, 0, 0, 0)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.textSize = 14f
                view.setTextColor(Color.parseColor("#1E2124"))
                view.setPadding(32, 24, 32, 24)
                view.setBackgroundResource(R.drawable.bg_spinner_dropdown)
                try {
                    view.typeface = android.graphics.Typeface.createFromAsset(assets, "font/lexend.ttf")
                } catch (e: Exception) {}
                return view
            }
        }
        binding.layoutCourse.spinnerCurriculum.adapter = adapter
        binding.layoutCourse.spinnerCurriculum.setPopupBackgroundResource(R.drawable.bg_spinner_dropdown)

        binding.layoutCourse.spinnerCurriculum.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = options[position]
                if (selected == "Curriculum") {
                    selectedCurriculum = null
                    academicLevelList.clear()
                    updateAcademicLevelSpinner()
                    return
                }
                if (selected == "+ Add Curriculum") {
                    showAddCurriculumDialog()
                    binding.layoutCourse.spinnerCurriculum.setSelection(0)
                } else {
                    selectedCurriculum = curriculumList[position - 1]
                    loadAcademicLevels()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadAcademicLevels() {
        selectedCurriculum?.let { curr ->
            db.collection("curriculums").document(curr).collection("academicLevels").get()
                .addOnSuccessListener { documents ->
                    academicLevelList.clear()
                    for (doc in documents) {
                        academicLevelList.add(doc.id)
                    }
                    updateAcademicLevelSpinner()
                }
        }
    }

    private fun updateAcademicLevelSpinner() {
        val options = mutableListOf<String>()
        options.add("Academic Level / Term") // Hint
        academicLevelList.forEach { options.add(it) }
        options.add("+ Add Academic Level/Term")

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 10f
                view.setTextColor(if (position == 0) Color.parseColor("#888888") else Color.parseColor("#1E2124"))
                view.textStyle = android.graphics.Typeface.BOLD
                try {
                    view.typeface = android.graphics.Typeface.createFromAsset(assets, "font/lexend.ttf")
                } catch (e: Exception) {}
                view.setPadding(0, 0, 0, 0)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.textSize = 14f
                view.setTextColor(Color.parseColor("#1E2124"))
                view.setPadding(32, 24, 32, 24)
                view.setBackgroundResource(R.drawable.bg_spinner_dropdown)
                try {
                    view.typeface = android.graphics.Typeface.createFromAsset(assets, "font/lexend.ttf")
                } catch (e: Exception) {}
                return view
            }
        }
        binding.layoutCourse.spinnerAcademicLevel.adapter = adapter
        binding.layoutCourse.spinnerAcademicLevel.setPopupBackgroundResource(R.drawable.bg_spinner_dropdown)

        binding.layoutCourse.spinnerAcademicLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = options[position]
                if (selected == "Academic Level / Term") {
                    selectedAcademicLevel = null
                    loadCourses()
                    return
                }
                if (selected == "+ Add Academic Level/Term") {
                    showAddAcademicLevelDialog()
                    binding.layoutCourse.spinnerAcademicLevel.setSelection(0)
                } else {
                    selectedAcademicLevel = academicLevelList[position - 1]
                    loadCourses()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private var TextView.textStyle: Int
        get() = typeface.style
        set(value) {
            setTypeface(typeface, value)
        }

    private fun showAddCurriculumDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_curriculum)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val input = dialog.findViewById<EditText>(R.id.et_year)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnAdd.setOnClickListener {
            val year = input.text.toString().trim()
            if (year.isNotEmpty()) {
                db.collection("curriculums").document(year).set(mapOf("year" to year))
                    .addOnSuccessListener { 
                        loadCurriculums() 
                        dialog.dismiss()
                    }
            }
        }
        btnCancel.setOnClickListener { 
            dialog.dismiss() 
        }
        dialog.show()
    }

    private fun showAddAcademicLevelDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_academic_level)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val yearSpinner = dialog.findViewById<Spinner>(R.id.spinner_year)
        val years = arrayOf("First Year", "2nd Year", "3rd Year", "4th Year", "5th Year")
        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)

        val semSpinner = dialog.findViewById<Spinner>(R.id.spinner_semester)
        val sems = arrayOf("First Semester", "2nd Semester", "Summer Term")
        semSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sems)

        val unitsInput = dialog.findViewById<EditText>(R.id.et_units)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnAdd.setOnClickListener {
            val yearStr = years[yearSpinner.selectedItemPosition]
            val sem = sems[semSpinner.selectedItemPosition]
            val units = unitsInput.text.toString().trim()
            val id = "$yearStr, $sem ($units Units)"
            
            selectedCurriculum?.let { curr ->
                db.collection("curriculums").document(curr).collection("academicLevels").document(id)
                    .set(mapOf("year" to yearStr, "semester" to sem, "units" to units))
                    .addOnSuccessListener { 
                        loadAcademicLevels() 
                        dialog.dismiss()
                    }
            }
        }
        btnCancel.setOnClickListener { 
            dialog.dismiss() 
        }
        dialog.show()
    }

    private fun showAddCourseDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_course)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val codeInput = dialog.findViewById<EditText>(R.id.et_course_code)
        val titleInput = dialog.findViewById<EditText>(R.id.et_descriptive_title)
        val labInput = dialog.findViewById<EditText>(R.id.et_lab_hours)
        val lecInput = dialog.findViewById<EditText>(R.id.et_lec_hours)
        val creditInput = dialog.findViewById<EditText>(R.id.et_credit_hours)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnAdd.setOnClickListener {
            val course = mapOf(
                "code" to codeInput.text.toString(),
                "title" to titleInput.text.toString(),
                "labHours" to labInput.text.toString(),
                "lecHours" to lecInput.text.toString(),
                "creditUnits" to creditInput.text.toString()
            )
            db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels")
                .document(selectedAcademicLevel!!).collection("courses").add(course)
                .addOnSuccessListener { 
                    loadCourses() 
                    dialog.dismiss()
                }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun loadCourses() {
        if (selectedCurriculum == null || selectedAcademicLevel == null) {
            binding.layoutCourse.llDynamicContent.visibility = View.GONE
            binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
            return
        }

        db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels")
            .document(selectedAcademicLevel!!).collection("courses").get()
            .addOnSuccessListener { documents ->
                val courseList = binding.layoutCourse.llCourseList
                courseList.removeAllViews()
                
                if (documents.isEmpty) {
                    binding.layoutCourse.llDynamicContent.visibility = View.VISIBLE
                    binding.layoutCourse.tvSemesterHeader.text = selectedAcademicLevel
                    binding.layoutCourse.llCourseList.visibility = View.GONE
                    binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
                    binding.layoutCourse.tvEmptyState.text = "No courses found for this term."
                } else {
                    binding.layoutCourse.llDynamicContent.visibility = View.VISIBLE
                    binding.layoutCourse.tvSemesterHeader.text = selectedAcademicLevel
                    binding.layoutCourse.llCourseList.visibility = View.VISIBLE
                    binding.layoutCourse.tvEmptyState.visibility = View.GONE

                    for (doc in documents) {
                        val view = LayoutInflater.from(this).inflate(R.layout.item_course, courseList, false)
                        view.findViewById<TextView>(R.id.tv_course_code).text = doc.getString("code")
                        view.findViewById<TextView>(R.id.tv_course_title).text = doc.getString("title")
                        val details = "Laboratory Hours: ${doc.getString("labHours")}\nLecture Hours: ${doc.getString("lecHours")}\nCredit Units: ${doc.getString("creditUnits")}"
                        view.findViewById<TextView>(R.id.tv_course_details).text = details
                        courseList.addView(view)
                    }
                }
            }
    }

    private fun updateNavUI(selected: String) {
        if (selected == currentNav) return

        val transition = AutoTransition()
        transition.duration = 250
        TransitionManager.beginDelayedTransition(binding.root, transition)

        val navMap = mapOf(
            "home" to Quadruple(binding.navHome, binding.ivHome, binding.tvHome, binding.layoutHome as View),
            "course" to Quadruple(binding.navCourse, binding.ivCourse, binding.tvCourse, binding.layoutCourse.root),
            "schedule" to Quadruple(binding.navSchedule, binding.ivSchedule, binding.tvSchedule, binding.layoutSchedule.root),
            "profile" to Quadruple(binding.navProfile, binding.ivProfile, binding.tvProfile, binding.layoutProfile.root)
        )

        navMap.forEach { (key, views) ->
            val (container, icon, text, layout) = views
            val params = container.layoutParams as LinearLayout.LayoutParams

            if (key == selected) {
                params.weight = 40f
                container.setBackgroundResource(R.drawable.bg_active_nav)
                icon.setColorFilter(Color.parseColor("#1E2124"))
                text.visibility = View.VISIBLE
                layout.visibility = View.VISIBLE
            } else {
                params.weight = 20f
                container.background = null
                icon.setColorFilter(Color.WHITE)
                text.visibility = View.GONE
                layout.visibility = View.GONE
            }
            container.layoutParams = params
        }

        currentNav = selected
        binding.scrollContent.smoothScrollTo(0, 0)
    }

    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: "User"
                        val role = document.getString("role") ?: "User"
                        binding.tvGreeting.text = if (role.equals("Admin", ignoreCase = true)) "Hello, Admin $firstName!" else "Hello, $firstName!"
                    }
                }
        }
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
