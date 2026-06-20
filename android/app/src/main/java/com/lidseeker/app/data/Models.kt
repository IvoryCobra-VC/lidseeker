package com.lidseeker.app.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class SearchResult(
    val type: String,            // "artist" | "album" | "track"
    val foreignId: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val albumType: String? = null,
    val imageUrl: String? = null,
    val inLibrary: Boolean = false,
    val requested: Boolean = false,
    // Track-only: the parent album a song belongs to.
    val albumForeignId: String? = null,
    val albumTitle: String? = null,
)

@Serializable
data class RequestBody(
    val type: String,
    val foreignId: String,
    // Track-only: the album a song request should add.
    val albumForeignId: String? = null,
    val mode: String? = null,    // reserved for the single-track path
)

@Serializable
data class Pipeline(
    val stage: String,           // requested | searching | downloading | importing | available
    val stageIndex: Int,         // position within `stages`
    val stages: List<String>,    // ordered stage keys for the stepper
    val percent: Float = 0f,     // overall album completion
    val trackFiles: Int = 0,
    val trackCount: Int = 0,
    val detail: String = "",     // human-readable current-stage line
    val failed: Boolean = false, // a download/import failed — offer retry
    val stuck: Boolean = false,  // no source found for a long time — offer retry
)

@Serializable
data class MusicRequest(
    val id: Int,
    val type: String,
    val foreignId: String,
    val title: String,
    val artist: String? = null,
    val imageUrl: String? = null,
    val status: String,          // pending | downloading | available | error
    val createdAt: String,
    val pipeline: Pipeline? = null,
)

@Serializable
data class DiscoverCategories(
    val genres: List<String> = emptyList(),
    val decades: List<Int> = emptyList(),
)

@Serializable
data class Track(
    val position: Int,
    val title: String,
    val durationMs: Int? = null,
    val mediumNumber: Int = 1,
)

@Serializable
data class ServiceLink(val name: String, val url: String)

@Serializable
data class ActionResult(val ok: Boolean = true, val message: String? = null)

@Serializable
data class AppSettings(
    val quality: String? = null,         // "mp3" | "flac"; null when the Soularr adapter is off
    val ntfyTopic: String? = null,
    val ntfyUrl: String? = null,
)

@Serializable
data class SettingsBody(val quality: String)
