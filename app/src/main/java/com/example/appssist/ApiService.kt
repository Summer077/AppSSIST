package com.example.appssist

import okhttp3.ResponseBody
import retrofit2.http.*

interface ApiService {
    @POST("api/auth/token/")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @GET("api/user-faculty-data/")
    suspend fun getUserFacultyData(): FacultyData

    @PATCH("api/user-profile-update/")
    suspend fun updateProfile(
        @Body data: @JvmSuppressWildcards Map<String, Any?>
    ): FacultyData

    @GET("api/dashboard-stats/")
    suspend fun getDashboardStats(): DashboardStats

    @GET("api/curriculums/")
    suspend fun getCurriculums(): List<CurriculumResponse>

    @GET("api/courses/")
    suspend fun getCourses(
        @Query("curriculum") curriculumId: Int?,
        @Query("year") yearLevel: Int?,
        @Query("semester") semester: Int?
    ): List<CourseResponse>

    @POST("api/courses/")
    suspend fun addCourse(
        @Body request: CourseRequest
    ): CourseResponse

    @PUT("api/courses/{id}/")
    suspend fun updateCourse(
        @Path("id") id: Int,
        @Body request: CourseRequest
    ): CourseResponse

    @DELETE("api/courses/{id}/")
    suspend fun deleteCourse(
        @Path("id") id: Int
    ): retrofit2.Response<Unit>

    @GET("api/sections/")
    suspend fun getSections(): List<SectionResponse>

    @POST("api/sections/")
    suspend fun addSection(
        @Body data: @JvmSuppressWildcards Map<String, Any?>
    ): SectionResponse

    @GET("api/faculty-list/")
    suspend fun getFacultyList(): List<FacultyListItem>

    @POST("api/faculty-list/")
    suspend fun addFaculty(
        @Body request: FacultyRequest
    ): FacultyListItem

    @GET("api/rooms/")
    suspend fun getRooms(): List<RoomResponse>

    @POST("api/rooms/")
    suspend fun addRoom(
        @Body data: @JvmSuppressWildcards Map<String, Any?>
    ): RoomResponse

    @GET("api/faculty/{id}/schedule-data/")
    suspend fun getFacultySchedule(
        @Path("id") facultyId: Int
    ): ScheduleListResponse

    @GET("api/section/{id}/schedule-data/")
    suspend fun getSectionSchedule(
        @Path("id") sectionId: Int
    ): ScheduleListResponse

    @GET("api/room/{id}/schedule-data/")
    suspend fun getRoomSchedule(
        @Path("id") roomId: Int
    ): ScheduleListResponse

    @GET("api/schedule/available-resources/")
    suspend fun getAvailableResources(): AvailableResourcesResponse

    @GET("api/schedule/staff/html/")
    suspend fun getStaffScheduleHtml(): ResponseBody

    @GET("api/schedule/faculty/{id}/html/")
    suspend fun getFacultyScheduleHtml(
        @Path("id") facultyId: Int
    ): ResponseBody

    @GET("api/schedule/section/{id}/html/")
    suspend fun getSectionScheduleHtml(
        @Path("id") sectionId: Int
    ): ResponseBody

    @GET("api/schedule/room/{id}/html/")
    suspend fun getRoomScheduleHtml(
        @Path("id") roomId: Int
    ): ResponseBody
}
