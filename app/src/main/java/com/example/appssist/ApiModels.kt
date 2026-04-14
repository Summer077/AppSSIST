package com.example.appssist

import com.google.gson.annotations.SerializedName

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
    val profile_picture_url: String?,
    @SerializedName("total_units")
    val total_units: Int = 0
)

data class FacultyListItem(
    val id: Int,
    val first_name: String?,
    val last_name: String?,
    val email: String? = null,
    @SerializedName(value = "total_units", alternate = ["units"])
    val total_units: Int = 0
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
    val color: String? = "#888888"
)

data class CourseRequest(
    val curriculum: Int,
    val course_code: String,
    val descriptive_title: String,
    val laboratory_hours: Int,
    val lecture_hours: Int,
    val credit_units: Int,
    val year_level: Int,
    val semester: Int,
    val color: String = "#3498db"
)

data class SectionResponse(
    val id: Int,
    val name: String,
    val year_level: Int,
    val semester: Int,
    val status: String,
    val curriculum: Int
)

data class RoomResponse(
    val id: Int,
    val name: String?,
    @SerializedName("room_number")
    val roomNumber: String?,
    @SerializedName("room_type")
    val roomType: String?,
    val capacity: Int?,
    val campus: String?
)

data class ScheduleListResponse(
    @SerializedName("schedules")
    val results: List<ScheduleItemResponse>?
)

data class ScheduleItemResponse(
    val id: Int,
    val day: Int?,
    @SerializedName("start_time")
    val startTime: String?,
    @SerializedName("end_time")
    val endTime: String?,
    
    // Flat fields from API
    @SerializedName("course_code") val courseCode: String?,
    @SerializedName("course_title") val courseTitle: String?,
    @SerializedName("course_color") val courseColor: String?,
    @SerializedName("room") val roomName: String?,
    @SerializedName("section_name") val sectionName: String?
)

data class AvailableResourcesResponse(
    val rooms: List<RoomResponse>?,
    val faculty: List<FacultyListItem>?,
    val sections: List<SectionResponse>?
)

data class DashboardStats(
    val faculty_count: Int,
    val section_count: Int
)
