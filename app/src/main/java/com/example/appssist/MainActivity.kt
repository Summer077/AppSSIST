package com.example.appssist

import android.app.Dialog
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.appssist.databinding.ActivityDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var currentNav = "home"

    private var curriculumList = mutableListOf<String>()
    private var academicLevelList = mutableListOf<String>()

    private var selectedCurriculum: String? = null
    private var selectedAcademicLevel: String? = null

    // Track the currently visible edit/delete buttons
    private var activeActionsView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fetchUserData()

        binding.tvTotalStaff.text = "0"
        binding.tvTotalSections.text = "0"

        setupQuickActions()
        setupBottomNavigation()
        setupCourseBackend()

        // Hide actions when clicking the main content card
        binding.layoutCourse.llMainContentCard.setOnClickListener {
            hideActiveActions()
        }

        loadRecentActivities()
    }

    private fun setupQuickActions() {
        binding.btnCreateSchedule.setOnClickListener {
            Toast.makeText(this, "Create Schedule clicked", Toast.LENGTH_SHORT).show()
            logActivity("You created a schedule.")
        }
        binding.btnAddCourse.setOnClickListener {
            handleCourseAddClick()
        }
        binding.btnAddStaff.setOnClickListener {
            Toast.makeText(this, "Add Staff clicked", Toast.LENGTH_SHORT).show()
            logActivity("You added a staff.")
        }
        binding.btnAddSection.setOnClickListener {
            Toast.makeText(this, "Add Section clicked", Toast.LENGTH_SHORT).show()
            logActivity("You added a section.")
        }
        binding.btnAddRoom.setOnClickListener {
            Toast.makeText(this, "Add Room clicked", Toast.LENGTH_SHORT).show()
            logActivity("You added a room.")
        }
    }

    private fun handleCourseAddClick() {
        if (selectedCurriculum != null && selectedAcademicLevel != null) {
            showAddCourseDialog()
        } else if (selectedCurriculum == null) {
            Toast.makeText(this, "Please select a Curriculum first", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please select an Academic Level / Term first", Toast.LENGTH_SHORT).show()
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

        updateCurriculumSpinner()
        updateAcademicLevelSpinner()

        loadCurriculums()
    }

    private fun hideActiveActions() {
        activeActionsView?.visibility = View.GONE
        activeActionsView = null
    }

    private fun loadCurriculums() {
        db.collection("curriculums").orderBy("year").get()
            .addOnSuccessListener { documents ->
                val previousSelection = selectedCurriculum
                curriculumList.clear()
                for (doc in documents) {
                    curriculumList.add(doc.id)
                }
                updateCurriculumSpinner()

                if (previousSelection != null && curriculumList.contains(previousSelection)) {
                    val pos = curriculumList.indexOf(previousSelection) + 1
                    binding.layoutCourse.spinnerCurriculum.setSelection(pos)
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error loading curriculums", e)
            }
    }

    private fun updateCurriculumSpinner() {
        val options = mutableListOf<String>()
        options.add("Curriculum")
        curriculumList.forEach { options.add("$it Curriculum") }
        options.add("+ Add Curriculum")

        val adapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_dropdown, R.id.tv_dropdown_text, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(R.id.tv_dropdown_text)
                view.findViewById<View>(R.id.v_divider).visibility = View.GONE

                textView.text = options[position]
                textView.textSize = 12f
                textView.setPadding(0, 0, 0, 0)
                textView.setTextColor(if (position == 0) Color.parseColor("#888888") else Color.parseColor("#1E2124"))
                try {
                    textView.typeface = ResourcesCompat.getFont(context, R.font.lexend)
                } catch (e: Exception) {}
                if (position != 0) textView.setTypeface(textView.typeface, Typeface.BOLD)

                view.findViewById<ImageView>(R.id.iv_edit_icon).visibility = View.GONE
                view.findViewById<ImageView>(R.id.iv_delete_icon).visibility = View.GONE

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                if (position == 0) {
                    val v = View(context)
                    v.visibility = View.GONE
                    v.layoutParams = AbsListView.LayoutParams(0, 0)
                    return v
                }

                val view = super.getDropDownView(position, null, parent)
                val textView = view.findViewById<TextView>(R.id.tv_dropdown_text)
                val divider = view.findViewById<View>(R.id.v_divider)
                val ivEdit = view.findViewById<ImageView>(R.id.iv_edit_icon)
                val ivDelete = view.findViewById<ImageView>(R.id.iv_delete_icon)

                textView.text = options[position]
                textView.setTextColor(Color.parseColor("#1E2124"))
                try {
                    textView.typeface = ResourcesCompat.getFont(context, R.font.lexend)
                } catch (e: Exception) {}

                divider.visibility = if (position == options.size - 1) View.GONE else View.VISIBLE

                if (position > 0 && position < options.size - 1) {
                    ivEdit.visibility = View.VISIBLE
                    ivDelete.visibility = View.VISIBLE

                    val curriculumName = curriculumList[position - 1]

                    ivEdit.setOnClickListener {
                        showEditCurriculumDialog(curriculumName)
                    }

                    ivDelete.setOnClickListener {
                        showDeleteCurriculumConfirmation(curriculumName)
                    }
                } else {
                    ivEdit.visibility = View.GONE
                    ivDelete.visibility = View.GONE
                }

                return view
            }
        }

        binding.layoutCourse.spinnerCurriculum.adapter = adapter
        binding.layoutCourse.spinnerCurriculum.setPopupBackgroundResource(R.drawable.bg_neumorphic_popup)

        binding.layoutCourse.spinnerCurriculum.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                hideActiveActions()
                if (position == 0) {
                    selectedCurriculum = null
                    academicLevelList.clear()
                    updateAcademicLevelSpinner()
                    loadCourses()
                    return
                }

                if (position == options.size - 1) {
                    showAddCurriculumDialog()
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
                    val previousSelection = selectedAcademicLevel
                    academicLevelList.clear()
                    for (doc in documents) {
                        academicLevelList.add(doc.id)
                    }
                    updateAcademicLevelSpinner()

                    if (previousSelection != null && academicLevelList.contains(previousSelection)) {
                        val pos = academicLevelList.indexOf(previousSelection) + 1
                        binding.layoutCourse.spinnerAcademicLevel.setSelection(pos)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Error loading academic levels", e)
                }
        }
    }

    private fun updateAcademicLevelSpinner() {
        val options = mutableListOf<String>()
        options.add("Academic Level / Term")

        academicLevelList.forEach {
            val cleanName = it.substringBefore(" (")
            options.add(cleanName)
        }

        options.add("+ Add Academic Level/Term")

        val adapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_dropdown, R.id.tv_dropdown_text, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(R.id.tv_dropdown_text)
                view.findViewById<View>(R.id.v_divider).visibility = View.GONE

                textView.text = options[position]
                textView.textSize = 11f
                textView.setPadding(0, 0, 0, 0)
                textView.setTextColor(if (position == 0) Color.parseColor("#888888") else Color.parseColor("#1E2124"))
                try {
                    textView.typeface = ResourcesCompat.getFont(context, R.font.lexend)
                } catch (e: Exception) {}
                if (position != 0) textView.setTypeface(textView.typeface, Typeface.BOLD)

                view.findViewById<ImageView>(R.id.iv_edit_icon).visibility = View.GONE
                view.findViewById<ImageView>(R.id.iv_delete_icon).visibility = View.GONE

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                if (position == 0) {
                    val v = View(context)
                    v.visibility = View.GONE
                    v.layoutParams = AbsListView.LayoutParams(0, 0)
                    return v
                }

                val view = super.getDropDownView(position, null, parent)
                val textView = view.findViewById<TextView>(R.id.tv_dropdown_text)
                val divider = view.findViewById<View>(R.id.v_divider)
                val ivEdit = view.findViewById<ImageView>(R.id.iv_edit_icon)
                val ivDelete = view.findViewById<ImageView>(R.id.iv_delete_icon)

                textView.text = options[position]
                textView.setTextColor(Color.parseColor("#1E2124"))
                try {
                    textView.typeface = ResourcesCompat.getFont(context, R.font.lexend)
                } catch (e: Exception) {}

                divider.visibility = if (position == options.size - 1) View.GONE else View.VISIBLE

                if (position > 0 && position < options.size - 1) {
                    ivEdit.visibility = View.VISIBLE
                    ivDelete.visibility = View.VISIBLE

                    val termId = academicLevelList[position - 1]

                    ivEdit.setOnClickListener {
                        showEditAcademicLevelDialog(termId)
                    }

                    ivDelete.setOnClickListener {
                        showDeleteAcademicLevelConfirmation(termId)
                    }
                } else {
                    ivEdit.visibility = View.GONE
                    ivDelete.visibility = View.GONE
                }

                return view
            }
        }

        binding.layoutCourse.spinnerAcademicLevel.adapter = adapter
        binding.layoutCourse.spinnerAcademicLevel.setPopupBackgroundResource(R.drawable.bg_neumorphic_popup)

        binding.layoutCourse.spinnerAcademicLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                hideActiveActions()
                if (position == 0) {
                    selectedAcademicLevel = null
                    loadCourses()
                    return
                }

                if (position == options.size - 1) {
                    showAddAcademicLevelDialog()
                } else {
                    selectedAcademicLevel = academicLevelList[position - 1]
                    loadCourses()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
                        Toast.makeText(this, "Curriculum $year added!", Toast.LENGTH_SHORT).show()
                        logActivity("You created a curriculum named $year.")
                        loadCurriculums()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Save Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
        btnCancel.setOnClickListener {
            binding.layoutCourse.spinnerCurriculum.setSelection(0)
            dialog.dismiss()
        }
        dialog.setOnCancelListener { binding.layoutCourse.spinnerCurriculum.setSelection(0) }
        dialog.show()
    }

    private fun showEditCurriculumDialog(oldYear: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_curriculum)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val input = dialog.findViewById<EditText>(R.id.et_year)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        input.setText(oldYear)
        btnAdd.text = "Update"

        btnAdd.setOnClickListener {
            val newYear = input.text.toString().trim()
            if (newYear.isNotEmpty() && newYear != oldYear) {
                // Rename logic: create new, delete old
                db.collection("curriculums").document(oldYear).get().addOnSuccessListener { doc ->
                    val data = doc.data ?: mapOf("year" to newYear)
                    val updatedData = data.toMutableMap()
                    updatedData["year"] = newYear

                    db.collection("curriculums").document(newYear).set(updatedData)
                        .addOnSuccessListener {
                            db.collection("curriculums").document(oldYear).delete()
                            Toast.makeText(this, "Curriculum updated to $newYear!", Toast.LENGTH_SHORT).show()
                            logActivity("You renamed curriculum $oldYear to $newYear.")
                            selectedCurriculum = newYear
                            loadCurriculums()
                            dialog.dismiss()
                        }
                }
            } else {
                dialog.dismiss()
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteCurriculumConfirmation(year: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Curriculum")
            .setMessage("Are you sure you want to delete the $year Curriculum? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("curriculums").document(year).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Curriculum deleted", Toast.LENGTH_SHORT).show()
                        logActivity("You deleted curriculum $year.")
                        selectedCurriculum = null
                        loadCurriculums()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddAcademicLevelDialog() {
        if (selectedCurriculum == null) {
            Toast.makeText(this, "Please select a Curriculum first!", Toast.LENGTH_SHORT).show()
            binding.layoutCourse.spinnerAcademicLevel.setSelection(0)
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_academic_level)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val yearSpinner = dialog.findViewById<Spinner>(R.id.spinner_year)
        val years = arrayOf("First Year", "Second Year", "Third Year", "Fourth Year", "Fifth Year")
        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)

        val semSpinner = dialog.findViewById<Spinner>(R.id.spinner_semester)
        val sems = arrayOf("First Semester", "Second Semester", "Summer Term")
        semSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sems)

        val unitsInput = dialog.findViewById<EditText>(R.id.et_units)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        btnAdd.setOnClickListener {
            val yearStr = years[yearSpinner.selectedItemPosition]
            val sem = sems[semSpinner.selectedItemPosition]
            val units = unitsInput.text.toString().trim()
            if (units.isEmpty()) {
                Toast.makeText(this, "Please enter total units", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val id = "$yearStr, $sem ($units Units)"

            db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels").document(id)
                .set(mapOf("year" to yearStr, "semester" to sem, "units" to units))
                .addOnSuccessListener {
                    Toast.makeText(this, "Term added to $selectedCurriculum!", Toast.LENGTH_SHORT).show()
                    logActivity("You added academic level: $id to $selectedCurriculum.")
                    loadAcademicLevels()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Save Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        btnCancel.setOnClickListener {
            binding.layoutCourse.spinnerAcademicLevel.setSelection(0)
            dialog.dismiss()
        }
        dialog.setOnCancelListener { binding.layoutCourse.spinnerAcademicLevel.setSelection(0) }
        dialog.show()
    }

    private fun showEditAcademicLevelDialog(oldId: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_academic_level)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val yearSpinner = dialog.findViewById<Spinner>(R.id.spinner_year)
        val years = arrayOf("First Year", "Second Year", "Third Year", "Fourth Year", "Fifth Year")
        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)

        val semSpinner = dialog.findViewById<Spinner>(R.id.spinner_semester)
        val sems = arrayOf("First Semester", "Second Semester", "Summer Term")
        semSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sems)

        val unitsInput = dialog.findViewById<EditText>(R.id.et_units)
        val btnAdd = dialog.findViewById<Button>(R.id.btn_add)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

        // Pre-fill existing values
        val yearPart = oldId.substringBefore(", ")
        val semPart = oldId.substringAfter(", ").substringBefore(" (")
        val unitsPart = oldId.substringAfter("(").substringBefore(" Units)")

        yearSpinner.setSelection(years.indexOf(yearPart).coerceAtLeast(0))
        semSpinner.setSelection(sems.indexOf(semPart).coerceAtLeast(0))
        unitsInput.setText(unitsPart)
        btnAdd.text = "Update"

        btnAdd.setOnClickListener {
            val yearStr = years[yearSpinner.selectedItemPosition]
            val sem = sems[semSpinner.selectedItemPosition]
            val units = unitsInput.text.toString().trim()
            val newId = "$yearStr, $sem ($units Units)"

            if (newId != oldId) {
                db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels").document(oldId).get()
                    .addOnSuccessListener { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["year"] = yearStr
                        data["semester"] = sem
                        data["units"] = units

                        db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels").document(newId).set(data)
                            .addOnSuccessListener {
                                db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels").document(oldId).delete()
                                Toast.makeText(this, "Term updated!", Toast.LENGTH_SHORT).show()
                                logActivity("You renamed term $oldId to $newId.")
                                selectedAcademicLevel = newId
                                loadAcademicLevels()
                                dialog.dismiss()
                            }
                    }
            } else {
                dialog.dismiss()
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteAcademicLevelConfirmation(id: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Term")
            .setMessage("Are you sure you want to delete $id? This will remove all associated courses.")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels").document(id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Term deleted", Toast.LENGTH_SHORT).show()
                        logActivity("You deleted term $id from $selectedCurriculum.")
                        selectedAcademicLevel = null
                        loadAcademicLevels()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCourseDialog(existingDocId: String? = null, existingData: Map<String, Any>? = null) {
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

        if (existingData != null) {
            btnAdd.text = "Update"
            codeInput.setText(existingData["code"].toString())
            titleInput.setText(existingData["title"].toString())
            val labVal = existingData["labHours"]?.toString() ?: ""
            val lecVal = existingData["lecHours"]?.toString() ?: ""
            val creditVal = existingData["creditUnits"]?.toString() ?: ""
            labInput.setText(labVal)
            lecInput.setText(lecVal)
            creditInput.setText(creditVal)
        }

        btnAdd.setOnClickListener {
            val code = codeInput.text.toString().trim()
            val title = titleInput.text.toString().trim()

            if (code.isEmpty() || title.isEmpty()) {
                Toast.makeText(this, "Code and Title are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val course = mapOf(
                "code" to code,
                "title" to title,
                "labHours" to labInput.text.toString().trim(),
                "lecHours" to lecInput.text.toString().trim(),
                "creditUnits" to creditInput.text.toString().trim()
            )

            val docRef = if (existingDocId != null) {
                db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels")
                    .document(selectedAcademicLevel!!).collection("courses").document(existingDocId)
            } else {
                db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels")
                    .document(selectedAcademicLevel!!).collection("courses").document()
            }

            docRef.set(course)
                .addOnSuccessListener {
                    val action = if (existingDocId != null) "updated" else "added"
                    Toast.makeText(this, "Course $action!", Toast.LENGTH_SHORT).show()
                    logActivity("You $action a course named $code.")
                    loadCourses()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Save Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun loadCourses() {
        if (selectedCurriculum == null || selectedAcademicLevel == null) {
            binding.layoutCourse.llDynamicContent.visibility = View.GONE
            binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
            binding.layoutCourse.tvEmptyState.text = "Select or add a curriculum or academic term."
            return
        }

        db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels")
            .document(selectedAcademicLevel!!).collection("courses").get()
            .addOnSuccessListener { documents ->
                val courseList = binding.layoutCourse.llCourseList
                courseList.removeAllViews()

                binding.layoutCourse.llDynamicContent.visibility = View.VISIBLE
                binding.layoutCourse.tvSemesterHeader.text = selectedAcademicLevel

                if (documents.isEmpty) {
                    binding.layoutCourse.llCourseList.visibility = View.GONE
                    binding.layoutCourse.tvEmptyState.visibility = View.VISIBLE
                    binding.layoutCourse.tvEmptyState.text = "No courses found for this term."
                } else {
                    binding.layoutCourse.llCourseList.visibility = View.VISIBLE
                    binding.layoutCourse.tvEmptyState.visibility = View.GONE

                    for (doc in documents) {
                        val view = LayoutInflater.from(this).inflate(R.layout.item_course, courseList, false)
                        val code = doc.getString("code") ?: ""
                        val title = doc.getString("title") ?: ""
                        val lab = doc.getString("labHours") ?: ""
                        val lec = doc.getString("lecHours") ?: ""
                        val credit = doc.getString("creditUnits") ?: ""

                        view.findViewById<TextView>(R.id.tv_course_code).text = code
                        view.findViewById<TextView>(R.id.tv_course_title).text = title
                        val details = "Laboratory Hours: $lab\nLecture Hours: $lec\nCredit Units: $credit"
                        view.findViewById<TextView>(R.id.tv_course_details).text = details

                        val actions = view.findViewById<LinearLayout>(R.id.ll_course_actions)
                        val btnEdit = view.findViewById<ImageButton>(R.id.btn_edit_course)
                        val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete_course)

                        view.setOnClickListener {
                            // If clicking the same item, toggle it. If clicking a new one, hide previous first.
                            if (activeActionsView == actions) {
                                hideActiveActions()
                            } else {
                                hideActiveActions()
                                actions.visibility = View.VISIBLE
                                activeActionsView = actions
                            }
                        }

                        btnEdit.setOnClickListener {
                            val data = mapOf("code" to code, "title" to title, "labHours" to lab, "lecHours" to lec, "creditUnits" to credit)
                            showAddCourseDialog(doc.id, data)
                        }

                        btnDelete.setOnClickListener {
                            AlertDialog.Builder(this)
                                .setTitle("Delete Course")
                                .setMessage("Are you sure you want to delete $code?")
                                .setPositiveButton("Delete") { _, _ ->
                                    db.collection("curriculums").document(selectedCurriculum!!).collection("academicLevels")
                                        .document(selectedAcademicLevel!!).collection("courses").document(doc.id).delete()
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Course deleted", Toast.LENGTH_SHORT).show()
                                            logActivity("You deleted a course named $code.")
                                            loadCourses()
                                        }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }

                        courseList.addView(view)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error loading courses", e)
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

    private fun logActivity(message: String) {
        val currentUser = auth.currentUser ?: return
        val activity = mapOf(
            "userId" to currentUser.uid,
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("activities").add(activity)
            .addOnSuccessListener {
                loadRecentActivities()
            }
    }

    private fun loadRecentActivities() {
        val currentUser = auth.currentUser ?: return

        // Get start of today in milliseconds
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        db.collection("activities")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                val list = binding.llRecentActivityList
                list.removeAllViews()

                // Filter and sort in memory to avoid composite index requirement
                val dailyActivities = documents.map { doc ->
                    Triple(
                        doc.getString("message") ?: "",
                        doc.getLong("timestamp") ?: 0L,
                        doc.id
                    )
                }.filter { it.second >= startOfToday }
                    .sortedByDescending { it.second }

                if (dailyActivities.isEmpty()) {
                    binding.tvNoRecentActivity.visibility = View.VISIBLE
                    binding.tvRecentActivityDateHeader.visibility = View.GONE
                } else {
                    binding.tvNoRecentActivity.visibility = View.GONE
                    binding.tvRecentActivityDateHeader.visibility = View.VISIBLE

                    val sdf = SimpleDateFormat("MM/dd/yyyy  h:mm a", Locale.getDefault())

                    for (activity in dailyActivities) {
                        val message = activity.first
                        val timestamp = activity.second
                        val dateStr = sdf.format(Date(timestamp))

                        val item = LayoutInflater.from(this).inflate(R.layout.item_recent_activity, list, false)
                        item.findViewById<TextView>(R.id.tv_activity_message).text = message
                        item.findViewById<TextView>(R.id.tv_activity_time).text = dateStr
                        list.addView(item)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error loading activities", e)
            }
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
