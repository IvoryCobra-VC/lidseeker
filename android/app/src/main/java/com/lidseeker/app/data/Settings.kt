package com.lidseeker.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lidseeker_settings")

/** Persists the backend base URL and the auth token. */
class Settings(private val context: Context) {
    private val keyServerUrl = stringPreferencesKey("server_url")
    private val keyToken = stringPreferencesKey("token")

    val serverUrl: Flow<String> = context.dataStore.data.map { it[keyServerUrl] ?: "" }
    val token: Flow<String> = context.dataStore.data.map { it[keyToken] ?: "" }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[keyServerUrl] = url.trim().trimEnd('/') }
    }

    suspend fun setToken(token: String) {
        context.dataStore.edit { it[keyToken] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(keyToken) }
    }
}
