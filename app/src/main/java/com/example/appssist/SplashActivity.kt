package com.example.appssist

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_splash)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error setting content view", e)
            // If the layout fails to load, just proceed to Login
            proceedToNextActivity()
            return
        }

        // Navigate after 2.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            proceedToNextActivity()
        }, 2500)
    }

    private fun proceedToNextActivity() {
        if (isFinishing) return
        
        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPrefs.getString("access_token", null)

        val intent = if (accessToken.isNullOrEmpty()) {
            Intent(this, LoginActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        
        startActivity(intent)
        finish()
    }
}
