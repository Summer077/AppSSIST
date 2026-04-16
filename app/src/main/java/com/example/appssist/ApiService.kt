package com.example.appssist

import okhttp3.ResponseBody
import retrofit2.http.*

interface ApiService {
    @POST("api/auth/token/")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @GET("api/user-faculty-data/")
    suspend fun getUserFacultyData(
        @Header("Authorization") authorization: String
    ): FacultyData

    @GET("api/dashboard-stats/")
    suspend fun getDashboardStats(
        @Header("Authorization") authorization: String
    ): DashboardStats

    @GET("api/curriculums/")
    suspend fun getCurriculums(
        @Header("Authorization") authorization: String
    ): List<CurriculumResponse>

    @GET("api/courses/")
    suspend fun getCourses(
        @Header("Authorization") authorization: String,
        @Query("curriculum") curriculumId: Int?,
        @Query("year") yearLevel: Int?,
        @Query("semester") semester: Int?
    ): List<CourseResponse>

    @POST("api/courses/")
    suspend fun addCourse(
        @Header("Authorization") authorization: String,
        @Body request: CourseRequest
    ): CourseResponse

    @PUT("api/courses/{id}/")
    suspend fun updateCourse(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String,
        @Body request: CourseRequest
    ): CourseResponse

    @DELETE("api/courses/{id}/")
    suspend fun deleteCourse(
        @Path("id") id: Int,
        @Header("Authorization") authorization: String
    ): retrofit2.Response<Unit>

    @GET("api/sections/")
    suspend fun getSections(
        @Header("Authorization") authorization: String
    ): List<SectionResponse>

    @GET("api/faculty-list/")
    suspend fun getFacultyList(
        @Header("Authorization") authorization: String
    ): List<FacultyListItem>

    @GET("api/rooms/")
    suspend fun getRooms(
        @Header("Authorization") authorization: String
    ): List<RoomResponse>

    @GET("api/faculty/{id}/schedule-data/")
    suspend fun getFacultySchedule(
        @Path("id") facultyId: Int,
        @Header("Authorization") authorization: String
    ): ScheduleListResponse

    @GET("api/section/{id}/schedule-data/")
    suspend fun getSectionSchedule(
        @Path("id") sectionId: Int,
        @Header("Authorization") authorization: String
    ): ScheduleListResponse

    @GET("api/room/{id}/schedule-data/")
    suspend fun getRoomSchedule(
        @Path("id") roomId: Int,
        @Header("Authorization") authorization: String
    ): ScheduleListResponse

    @GET("api/schedule/available-resources/")
    suspend fun getAvailableResources(
        @Header("Authorization") authorization: String
    ): AvailableResourcesResponse

    @GET("api/schedule/staff/html/")
    suspend fun getStaffScheduleHtml(
        @Header("Authorization") authorization: String
    ): ResponseBody

    @GET("api/schedule/faculty/{id}/html/")
    suspend fun getFacultyScheduleHtml(
        @Path("id") facultyId: Int,
        @Header("Authorization") authorization: String
    ): ResponseBody

    @GET("api/schedule/section/{id}/html/")
    suspend fun getSectionScheduleHtml(
        @Path("id") sectionId: Int,
        @Header("Authorization") authorization: String
    ): ResponseBody

    @GET("api/schedule/room/{id}/html/")
    suspend fun getRoomScheduleHtml(
        @Path("id") roomId: Int,
        @Header("Authorization") authorization: String
    ): ResponseBody
}

data class ScheduleHtmlResponse(
    val success: Boolean,
    val html: String,
    val faculty_name: String,
    val total_lec: Int,
    val total_lab: Int,
    val total_units: Int
)
