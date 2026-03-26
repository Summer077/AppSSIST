package com.example.appssist

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Create Superuser if not exists (One-time setup for development)
        createSuperUserIfNeeded()

        // Navigate to LoginActivity after 2.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 2500)
    }

    private fun createSuperUserIfNeeded() {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        
        val firstName = "Stephen Nash"
        val lastName = "Baldonado"
        val role = "ADMIN"
        
        // Generate Username: A + s (Stephen) + n (Nash) + baldonado + random digit
        val firstChars = firstName.split(" ").joinToString("") { it.take(1).lowercase() }
        val username = "A${firstChars}${lastName.lowercase()}1" // Hardcoded 1 for consistency in demo
        val email = "admin@appssist.com"
        val password = "Password123"

        firestore.collection("users").whereEqualTo("username", username).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Create user in Firebase Auth first
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = task.result?.user?.uid ?: ""
                                val userMap = hashMapOf(
                                    "uid" to uid,
                                    "firstName" to firstName,
                                    "lastName" to lastName,
                                    "username" to username,
                                    "email" to email,
                                    "role" to role
                                )
                                firestore.collection("users").document(uid).set(userMap)
                                    .addOnSuccessListener {
                                        Log.d("SplashActivity", "Superuser created: $username")
                                    }
                            }
                        }
                }
            }
    }
}
