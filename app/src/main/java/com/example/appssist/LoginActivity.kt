package com.example.appssist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.appssist.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var isPasswordVisible = false
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        RetrofitClient.init(this)
        tokenManager = TokenManager(this)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPrefs.getString("remembered_email", "")
        if (!savedEmail.isNullOrEmpty()) {
            binding.etUsername.setText(savedEmail)
            binding.cbRemember.isChecked = true
        }

        binding.ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.transformationMethod =
                    HideReturnsTransformationMethod.getInstance()
                binding.ivPasswordToggle.alpha = 0.5f
            } else {
                binding.etPassword.transformationMethod =
                    PasswordTransformationMethod.getInstance()
                binding.ivPasswordToggle.alpha = 1.0f
            }
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.login(
                    LoginRequest(username = email, password = password)
                )

                val accessToken = response.access
                tokenManager.saveToken(accessToken)

                val sharedPrefs = getSharedPreferences("AppSSIST_Prefs", Context.MODE_PRIVATE)
                val editor = sharedPrefs.edit()

                if (binding.cbRemember.isChecked) {
                    editor.putString("remembered_email", email)
                } else {
                    editor.remove("remembered_email")
                }
                editor.apply()

                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("LoginActivity", "Login failed with code ${e.code()}: $errorBody")
                
                val errorMessage = try {
                    val json = JSONObject(errorBody ?: "")
                    json.optString("detail", "Login failed: ${e.code()}")
                } catch (ex: Exception) {
                    "Login failed: ${e.code()}"
                }
                
                Toast.makeText(
                    this@LoginActivity,
                    errorMessage,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e("LoginActivity", "Login error: ${e.message}", e)
                Toast.makeText(
                    this@LoginActivity,
                    "Login error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
