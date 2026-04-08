package com.example.appssist

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

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

    @GET("api/sections/")
    suspend fun getSections(
        @Header("Authorization") authorization: String
    ): List<SectionResponse>
}
