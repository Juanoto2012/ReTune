package com.maloy.muzza.utils

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

object SpotifyHelper {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @Serializable
    data class AuthResponse(
        @SerialName("accessToken") val accessToken: String,
        @SerialName("accessTokenExpirationTimestampMs") val expirationMs: Long? = null
    )

    @Serializable
    data class OldAuthResponse(
        @SerialName("access_token") val accessToken: String
    )

    suspend fun getAccessTokenWithCredentials(clientId: String, clientSecret: String, code: String): String {
        val response: OldAuthResponse = client.post("https://accounts.spotify.com/api/token") {
            basicAuth(clientId, clientSecret)
            setBody(FormDataContent(Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", "http://127.0.0.1:45454")
            }))
        }.body()
        return response.accessToken
    }

    suspend fun getAccessTokenWithCookie(spDc: String): String {
        val response: AuthResponse = client.get("https://open.spotify.com/get_access_token?reason=transport&productType=web_player") {
            header("Cookie", "sp_dc=$spDc")
            header("User-Agent", USER_AGENT)
            header("App-Platform", "WebPlayer")
        }.body()
        return response.accessToken
    }

    @Serializable
    data class UserProfile(val display_name: String? = null)

    suspend fun getUserProfile(token: String): UserProfile {
        return client.get("https://api.spotify.com/v1/me") {
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
        val tracks: TrackCount? = null
    )

    @Serializable
    data class TrackCount(val total: Int)

    @Serializable
    data class Image(val url: String)

    suspend fun getPlaylists(token: String, offset: Int = 0): PlaylistResponse {
        return client.get("https://api.spotify.com/v1/me/playlists?offset=$offset&limit=50") {
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
    data class TrackContainer(val track: Track? = null)

    @Serializable
    data class Track(
        val id: String? = null,
        val name: String? = null,
        val artists: List<Artist> = emptyList()
    )

    @Serializable
    data class Artist(val name: String? = null)

    suspend fun getPlaylistTracks(token: String, playlistId: String, url: String? = null): TracksResponse {
        val requestUrl = url ?: "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
        return client.get(requestUrl) {
            header("Authorization", "Bearer $token")
        }.body()
    }

    suspend fun getLikedSongs(token: String, url: String? = null): TracksResponse {
        val requestUrl = url ?: "https://api.spotify.com/v1/me/tracks?limit=50"
        return client.get(requestUrl) {
            header("Authorization", "Bearer $token")
        }.body()
    }
    
    suspend fun fetchAnonymousToken(): String {
        val response: AuthResponse = client.get("https://open.spotify.com/get_access_token?reason=transport&productType=embed") {
            header("User-Agent", USER_AGENT)
            header("Referer", "https://open.spotify.com/")
        }.body()
        return response.accessToken
    }
}
