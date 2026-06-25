package com.lidseeker.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @GET("api/search")
    suspend fun search(
        @Query("term") term: String,
        @Query("type") type: String,
    ): List<SearchResult>

    @GET("api/artist/{foreignId}/albums")
    suspend fun artistAlbums(@Path("foreignId") foreignId: String): List<SearchResult>

    @GET("api/album/{foreignId}/tracks")
    suspend fun albumTracks(@Path("foreignId") foreignId: String): List<Track>

    @POST("api/request")
    suspend fun request(@Body body: RequestBody): MusicRequest

    @GET("api/requests")
    suspend fun requests(): List<MusicRequest>

    @GET("api/services")
    suspend fun services(): List<ServiceLink>

    @POST("api/soularr/run")
    suspend fun forceSoularr(): ActionResult

    @GET("api/discover")
    suspend fun discover(
        @Query("genre") genre: String? = null,
        @Query("decade") decade: Int? = null,
    ): List<SearchResult>

    @GET("api/discover/categories")
    suspend fun discoverCategories(
        @Query("genre") genre: String? = null,
        @Query("decade") decade: Int? = null,
    ): DiscoverCategories

    @DELETE("api/requests/{id}")
    suspend fun deleteRequest(@Path("id") id: Int): ActionResult

    @POST("api/requests/{id}/retry")
    suspend fun retryRequest(@Path("id") id: Int): ActionResult

    @GET("api/settings")
    suspend fun getSettings(): AppSettings

    @PUT("api/settings")
    suspend fun putSettings(@Body body: SettingsBody): ActionResult

    @PUT("api/me/password")
    suspend fun changePassword(@Body body: PasswordChangeBody): ActionResult

    @POST("api/requests/clear-available")
    suspend fun clearAvailable(): ActionResult

    @POST("api/requests/{id}/search-now")
    suspend fun searchRequestNow(@Path("id") id: Int): ActionResult

    @GET("api/stats")
    suspend fun stats(): StatsOut
}
