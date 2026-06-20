package com.lidseeker.app.data

import kotlinx.coroutines.flow.first

/**
 * Single source of truth for connection config and Lidseeker API calls.
 * Holds the base URL + token in memory (read by the OkHttp interceptor) and
 * mirrors them to [Settings] for persistence.
 */
class Repository(private val settings: Settings) {
    @Volatile private var baseUrl: String = ""
    @Volatile private var token: String = ""

    private val api: ApiService = ApiClient.create { ConnConfig(baseUrl, token) }

    /** Load persisted config once at startup. */
    suspend fun bootstrap() {
        baseUrl = settings.serverUrl.first()
        token = settings.token.first()
    }

    fun hasServer(): Boolean = baseUrl.isNotEmpty()
    fun isLoggedIn(): Boolean = token.isNotEmpty()
    fun currentServerUrl(): String = baseUrl

    suspend fun saveServerUrl(url: String) {
        settings.setServerUrl(url)
        baseUrl = url.trim().trimEnd('/')
    }

    suspend fun login(username: String, password: String) {
        val res = api.login(LoginRequest(username, password))
        token = res.token
        settings.setToken(res.token)
    }

    suspend fun logout() {
        token = ""
        settings.clearToken()
    }

    suspend fun search(term: String, type: String): List<SearchResult> =
        api.search(term, type)

    suspend fun artistAlbums(foreignId: String): List<SearchResult> =
        api.artistAlbums(foreignId)

    suspend fun albumTracks(foreignId: String): List<Track> =
        api.albumTracks(foreignId)

    suspend fun request(
        type: String,
        foreignId: String,
        albumForeignId: String? = null,
    ): MusicRequest =
        api.request(RequestBody(type, foreignId, albumForeignId = albumForeignId))

    suspend fun requests(): List<MusicRequest> = api.requests()

    suspend fun services(): List<ServiceLink> = api.services()

    suspend fun forceSoularr(): ActionResult = api.forceSoularr()

    suspend fun discover(genre: String? = null, decade: Int? = null): List<SearchResult> =
        api.discover(genre, decade)

    suspend fun discoverCategories(genre: String? = null, decade: Int? = null): DiscoverCategories =
        api.discoverCategories(genre, decade)

    suspend fun deleteRequest(id: Int): ActionResult = api.deleteRequest(id)

    suspend fun retryRequest(id: Int): ActionResult = api.retryRequest(id)

    suspend fun getSettings(): AppSettings = api.getSettings()

    suspend fun setQuality(quality: String): ActionResult = api.putSettings(SettingsBody(quality))
}
