package com.example.appssist

import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
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
    }

    private fun setupQuickActions() {
        binding.btnCreateSchedule.setOnClickListener {
            Toast.makeText(this, "Create Schedule clicked", Toast.LENGTH_SHORT).show()
        }
        binding.btnAddCourse.setOnClickListener {
            Toast.makeText(this, "Add Course clicked", Toast.LENGTH_SHORT).show()
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

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener { updateNavUI("home") }
        binding.navCourse.setOnClickListener { updateNavUI("course") }
        binding.navSchedule.setOnClickListener { updateNavUI("schedule") }
        binding.navProfile.setOnClickListener { updateNavUI("profile") }
    }

    private fun updateNavUI(selected: String) {
        if (selected == currentNav) return

        // Smooth transition
        val transition = AutoTransition()
        transition.duration = 250
        // Transition for both bottom nav and content container
        TransitionManager.beginDelayedTransition(binding.root, transition)

        val navMap = mapOf(
            "home" to Quadruple(binding.navHome, binding.ivHome, binding.tvHome, binding.layoutHome),
            "course" to Quadruple(binding.navCourse, binding.ivCourse, binding.tvCourse, binding.layoutCourse),
            "schedule" to Quadruple(binding.navSchedule, binding.ivSchedule, binding.tvSchedule, binding.layoutSchedule),
            "profile" to Quadruple(binding.navProfile, binding.ivProfile, binding.tvProfile, binding.layoutProfile)
        )

        // Reset and Animate Weights and Visibility
        navMap.forEach { (key, views) ->
            val (container, icon, text, layout) = views
            val params = container.layoutParams as LinearLayout.LayoutParams

            if (key == selected) {
                // Active Nav: Takes more space (weight 40)
                params.weight = 40f
                container.setBackgroundResource(R.drawable.bg_active_nav)
                icon.setColorFilter(Color.parseColor("#1E2124"))
                text.visibility = View.VISIBLE
                
                // Show corresponding layout
                layout.visibility = View.VISIBLE
            } else {
                // Inactive Nav: Takes less space (weight 20)
                params.weight = 20f
                container.background = null
                icon.setColorFilter(Color.WHITE)
                text.visibility = View.GONE
                
                // Hide other layouts
                layout.visibility = View.GONE
            }
            container.layoutParams = params
        }

        currentNav = selected
        
        // Scroll to top when switching tabs
        binding.scrollContent.smoothScrollTo(0, 0)
    }

    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: "User"
                        val role = document.getString("role") ?: "User"
                        
                        if (role.equals("Admin", ignoreCase = true)) {
                            binding.tvGreeting.text = "Hello, Admin $firstName!"
                        } else {
                            binding.tvGreeting.text = "Hello, $firstName!"
                        }
                    } else {
                        val emailPrefix = currentUser.email?.substringBefore("@")
                            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } 
                            ?: "User"
                        binding.tvGreeting.text = "Hello, $emailPrefix!"
                    }
                }
                .addOnFailureListener { e ->
                    binding.tvGreeting.text = "Hello!"
                }
        } else {
            binding.tvGreeting.text = "Hello, Guest!"
        }
    }

    // Helper class for 4 values
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
