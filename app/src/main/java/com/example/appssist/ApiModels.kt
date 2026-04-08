package com.example.appssist

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenResponse(
    val access: String,
    val refresh: String
)

data class FacultyData(
    val id: Int,
    val first_name: String,
    val last_name: String,
    val email: String,
    val gender: String?,
    val profile_picture_url: String?
)

data class CurriculumResponse(
    val id: Int,
    val name: String,
    val year: Int
)

data class CourseResponse(
    val id: Int,
    val course_code: String,
    val descriptive_title: String,
    val lecture_hours: Int,
    val laboratory_hours: Int,
    val credit_units: Int,
    val year_level: Int,
    val semester: Int,
    val color: String
)

data class SectionResponse(
    val id: Int,
    val name: String,
    val year_level: Int,
    val semester: Int,
    val status: String,
    val curriculum: Int
)

data class DashboardStats(
    val faculty_count: Int,
    val section_count: Int
)
