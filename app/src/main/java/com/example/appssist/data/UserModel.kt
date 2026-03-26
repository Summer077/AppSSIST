package com.example.appssist.data

data class UserModel(
    val uid: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val username: String = "",
    val role: String = "STAFF", // "ADMIN" or "STAFF"
    val profileImageUrl: String? = null
)
