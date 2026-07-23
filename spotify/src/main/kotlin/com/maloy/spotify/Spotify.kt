package com.maloy.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.basicAuth
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Spotify {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://open.spotify.com/"

    @Serializable
    data class AuthResponse(
        @SerialName("accessToken") val accessToken: String,
        @SerialName("token_type") val tokenType: String? = null
    )

    // REQUISITO 1: Token Anónimo
    suspend fun fetchAnonymousToken(): Result<String> = runCatching {
        val response: AuthResponse = client.get("https://open.spotify.com/get_access_token?reason=transport&productType=embed") {
            header("User-Agent", USER_AGENT)
            header("Referer", REFERER)
        }.body()
        response.accessToken
    }

    // MÉTODO TRADICIONAL
    suspend fun getAccessTokenWithCredentials(clientId: String, clientSecret: String, code: String): Result<String> = runCatching {
        val response: AuthResponse = client.post("https://accounts.spotify.com/api/token") {
            basicAuth(clientId, clientSecret)
            setBody(FormDataContent(Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", "http://127.0.0.1:45454")
            }))
        }.body()
        response.accessToken
    }

    // MÉTODO COOKIE
    suspend fun getAccessTokenWithCookie(spDc: String): Result<String> = runCatching {
        val response: AuthResponse = client.get("https://open.spotify.com/get_access_token?reason=transport&productType=web_player") {
            header("Cookie", "sp_dc=$spDc")
            header("User-Agent", USER_AGENT)
        }.body()
        response.accessToken
    }

    @Serializable
    data class UserProfile(
        @SerialName("display_name") val displayName: String? = null
    )

    suspend fun getUserProfile(token: String): Result<UserProfile> = runCatching {
        client.get("https://api.spotify.com/v1/me") {
            header("Authorization", "Bearer $token")
        }.body()
    }

    @Serializable
    data class PlaylistResponse(
        val items: List<PlaylistItem> = emptyList(),
        val next: String? = null,
        val total: Int? = null
    )

    @Serializable
    data class PlaylistItem(
        val id: String,
        val name: String,
        val images: List<Image> = emptyList(),
        @SerialName("tracks") val tracksInfo: TrackCount? = null
    ) {
        @Serializable
        data class TrackCount(val total: Int)
    }

    @Serializable
    data class Image(val url: String)

    suspend fun getPlaylists(token: String, offset: Int = 0, limit: Int = 50): Result<PlaylistResponse> = runCatching {
        client.get("https://api.spotify.com/v1/me/playlists?offset=$offset&limit=$limit") {
            header("Authorization", "Bearer $token")
        }.body()
    }

    @Serializable
    data class TracksResponse(
        val items: List<TrackContainer> = emptyList(),
        val next: String? = null,
        val total: Int? = null
    )

    @Serializable
    data class TrackContainer(
        val track: Track? = null
    )

    @Serializable
    data class Track(
        val id: String? = null,
        val name: String? = null,
        val artists: List<Artist> = emptyList(),
        @SerialName("duration_ms") val durationMs: Long? = 0
    )

    @Serializable
    data class Artist(val name: String? = null)

    // REQUISITO 2: Extracción con Paginación
    suspend fun getAllPlaylistTracks(token: String, playlistId: String): Result<List<Track>> = runCatching {
        val allTracks = mutableListOf<Track>()
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"

        while (nextUrl != null) {
            val response: TracksResponse = client.get(nextUrl!!) {
                header("Authorization", "Bearer $token")
            }.body()
            allTracks.addAll(response.items.mapNotNull { it.track })
            nextUrl = response.next
        }
        allTracks
    }

    suspend fun getLikedSongs(token: String, url: String? = null): Result<TracksResponse> = runCatching {
        val requestUrl = url ?: "https://api.spotify.com/v1/me/tracks?limit=50"
        client.get(requestUrl) {
            header("Authorization", "Bearer $token")
        }.body()
    }
}
