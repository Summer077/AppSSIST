package com.example.appssist

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.appssist.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isPasswordVisible = false

    // Custom transformation to hide characters immediately (no delay)
    private val instantHideTransformation = object : PasswordTransformationMethod() {
        override fun getTransformation(source: CharSequence, view: View): CharSequence {
            return PasswordCharSequence(source)
        }

        inner class PasswordCharSequence(private val source: CharSequence) : CharSequence {
            override val length: Int get() = source.length
            override fun get(index: Int): Char = '●'
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                return PasswordCharSequence(source.subSequence(startIndex, endIndex))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure password is masked initially with the custom instant hide transformation
        binding.etPassword.transformationMethod = instantHideTransformation

        // Password visibility toggle
        binding.ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                // Show password
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.ivPasswordToggle.alpha = 0.5f // Visual feedback that it's toggled
            } else {
                // Hide password immediately
                binding.etPassword.transformationMethod = instantHideTransformation
                binding.ivPasswordToggle.alpha = 1.0f
            }
            // Move cursor to end
            binding.etPassword.text?.let {
                binding.etPassword.setSelection(it.length)
            }
        }

        // Login button — navigates to MainActivity
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Forgot password (placeholder)
        binding.tvForgot.setOnClickListener {
            // TODO: implement forgot password flow
        }
    }
}