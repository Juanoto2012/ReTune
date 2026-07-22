package com.maloy.muzza.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.db.entities.PlaylistSongMap
import com.maloy.muzza.db.entities.SongArtistMap
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.spotify.tracks.TrackItem
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.ImportFromSpotifyScreenState
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.ImportProgressEvent
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.Playlist
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.ui.utils.toHighResThumbnail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class ImportFromSpotifyViewModel @Inject constructor(
    private val localDatabase: MusicDatabase
) : ViewModel() {
    val importFromSpotifyScreenState = mutableStateOf(
        ImportFromSpotifyScreenState(
            isRequesting = false,
            accessToken = "",
            error = false,
            exception = null,
            userName = "",
            isObtainingAccessTokenSuccessful = false,
            playlists = emptyList(),
            totalPlaylistsCount = 0,
            reachedEndForPlaylistPagination = false
        )
    )
    val selectedPlaylists = mutableStateListOf<Playlist>()
    val isLikedSongsSelectedForImport = mutableStateOf(false)
    val isImportingCompleted = mutableStateOf(false)
    val isImportingInProgress = mutableStateOf(false)

    // Método Tradicional
    fun loginWithCredentials(clientId: String, clientSecret: String, code: String) {
        viewModelScope.launch {
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true, error = false)
            com.maloy.spotify.Spotify.getAccessTokenWithCredentials(clientId, clientSecret, code).onSuccess { token ->
                fetchInitialData(token)
            }.onFailure { 
                handleError(it)
            }
        }
    }

    // Método Cookie (Vivi)
    fun fetchPlaylistsWithSpDc(spDc: String) {
        viewModelScope.launch {
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true, error = false)
            com.maloy.spotify.Spotify.getAccessTokenWithCookie(spDc).onSuccess { token ->
                fetchInitialData(token)
            }.onFailure { 
                handleError(it)
            }
        }
    }

    private suspend fun fetchInitialData(token: String) {
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            accessToken = token,
            isObtainingAccessTokenSuccessful = true
        )

        com.maloy.spotify.Spotify.getUserProfile(token).onSuccess { profile ->
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                userName = profile.displayName ?: "Spotify User"
            )
        }

        com.maloy.spotify.Spotify.getPlaylists(token).onSuccess { response ->
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                playlists = response.items.map { 
                    com.maloy.muzza.models.spotify.playlists.SpotifyPlaylistItem(
                        playlistDescription = "",
                        playlistId = it.id,
                        playlistName = it.name,
                        images = it.images.map { img -> com.maloy.muzza.models.spotify.playlists.Images(img.url) },
                        tracks = com.maloy.muzza.models.spotify.Tracks(it.tracksInfo?.total ?: 0),
                        type = "playlist",
                        uri = "spotify:playlist:${it.id}"
                    )
                },
                totalPlaylistsCount = response.total ?: 0,
                reachedEndForPlaylistPagination = response.next == null,
                isRequesting = false
            )
        }.onFailure { handleError(it) }
    }

    private fun handleError(e: Throwable) {
        Timber.e(e)
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            isRequesting = false,
            error = true,
            exception = if (e is Exception) e else Exception(e.message)
        )
    }

    private var paginatedResultsLimit = 50
    private var playListPaginationOffset = 0

    fun retrieveNextPageOfPlaylists() {
        viewModelScope.launch {
            val token = importFromSpotifyScreenState.value.accessToken
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true)
            
            playListPaginationOffset += paginatedResultsLimit
            com.maloy.spotify.Spotify.getPlaylists(token, offset = playListPaginationOffset).onSuccess { response ->
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    playlists = importFromSpotifyScreenState.value.playlists + response.items.map {
                        com.maloy.muzza.models.spotify.playlists.SpotifyPlaylistItem(
                            playlistDescription = "",
                            playlistId = it.id,
                            playlistName = it.name,
                            images = it.images.map { img -> com.maloy.muzza.models.spotify.playlists.Images(img.url) },
                            type = "playlist",
                            uri = "spotify:playlist:${it.id}"
                        )
                    },
                    totalPlaylistsCount = response.total ?: 0,
                    reachedEndForPlaylistPagination = response.next == null,
                    isRequesting = false
                )
            }.onFailure {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = false)
            }
        }
    }

    fun importSelectedItems(saveInDefaultLikedSongs: Boolean?) {
        importLogs.clear()
        isImportingCompleted.value = false
        isImportingInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            supervisorScope {
                logTheString("Starting the import process")
                val likedSongsJob = launch {
                    saveInDefaultLikedSongs?.let { importSpotifyLikedSongs(it) }
                }

                val playlistsJob = launch {
                    importPlaylists(selectedPlaylists, importFromSpotifyScreenState.value.accessToken)
                }
                likedSongsJob.join()
                playlistsJob.join()
                logTheString("Import Succeeded!")
                isImportingCompleted.value = true
                isImportingInProgress.value = false
            }
        }
    }

    private suspend fun importPlaylists(selectedPlaylists: List<Playlist>, authToken: String) = supervisorScope {
        selectedPlaylists.forEachIndexed { playlistIndex, playlist ->
            var progressedTracksCount = 0
            val generatedPlaylistId = PlaylistEntity.generatePlaylistId()
            localDatabase.insert(PlaylistEntity(
                id = generatedPlaylistId, 
                name = playlist.name,
                bookmarkedAt = LocalDateTime.now()
            ))
            
            val tracks = getTracksFromAPlaylist(playlist.id, authToken)
            tracks.forEach { trackItem ->
                launch {
                    val query = "${trackItem.trackName} ${trackItem.artists.firstOrNull()?.name ?: ""}"
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->
                        val song = result.items.firstOrNull() as? SongItem ?: return@onSuccess
                        
                        // Insertar canción con metadatos completos y marcar en la librería
                        localDatabase.transaction {
                            insert(song.toMediaMetadata()) {
                                it.copy(inLibrary = LocalDateTime.now())
                            }
                            insert(PlaylistSongMap(playlistId = generatedPlaylistId, songId = song.id))
                        }
                        
                        _playlistsImportProgress.emit(ImportProgressEvent.PlaylistsProgress(
                            completed = false,
                            progressedTrackCount = ++progressedTracksCount,
                            playlistName = playlist.name,
                            totalTracksCount = tracks.size,
                            currentPlaylistIndex = playlistIndex
                        ))
                    }
                }
            }
        }
    }

    private suspend fun getTracksFromAPlaylist(spotifyPlaylistId: String, authToken: String): List<TrackItem> {
        val tracks = mutableListOf<TrackItem>()
        var nextUrl: String? = null
        do {
            com.maloy.spotify.Spotify.getPlaylistTracks(authToken, spotifyPlaylistId, nextUrl).onSuccess { response ->
                tracks.addAll(response.items.mapNotNull { it.track?.let { t ->
                    TrackItem(
                        trackName = t.name ?: "Unknown",
                        artists = t.artists.map { a -> com.maloy.muzza.models.spotify.tracks.Artist(a.name ?: "Unknown") },
                        trackId = t.id ?: "",
                        isLocal = false,
                        type = "track"
                    )
                }})
                nextUrl = response.next
            }.onFailure { nextUrl = null }
        } while (nextUrl != null)
        return tracks
    }

    private suspend fun importSpotifyLikedSongs(saveInDefaultLikedSongs: Boolean): Unit = supervisorScope {
        var nextUrl: String? = null
        var totalSongsCount = -1
        val progressedTracks = AtomicInteger(0)
        do {
            com.maloy.spotify.Spotify.getLikedSongs(importFromSpotifyScreenState.value.accessToken, nextUrl).onSuccess { response ->
                totalSongsCount = response.total ?: 0
                nextUrl = response.next
                response.items.mapNotNull { it.track }.map { likedSong ->
                    async {
                        val query = "${likedSong.name ?: ""} ${likedSong.artists.firstOrNull()?.name ?: ""}"
                        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->
                            val song = result.items.firstOrNull() as? SongItem ?: return@onSuccess
                            localDatabase.transaction {
                                insert(song.toMediaMetadata()) {
                                    it.copy(
                                        liked = saveInDefaultLikedSongs,
                                        inLibrary = if (saveInDefaultLikedSongs) LocalDateTime.now() else it.inLibrary
                                    )
                                }
                            }
                            _likedSongsImportProgress.emit(ImportProgressEvent.LikedSongsProgress(
                                completed = false,
                                currentCount = progressedTracks.incrementAndGet(),
                                totalTracksCount = totalSongsCount
                            ))
                        }
                    }
                }.awaitAll()
            }.onFailure { nextUrl = null }
        } while (nextUrl != null)
    }

    private fun logTheString(string: String) { Timber.tag("Muzza Log").d(string) }

    private val _likedSongsImportProgress = MutableStateFlow(ImportProgressEvent.LikedSongsProgress(false, 0, 0))
    val importLogs = mutableStateListOf<String>()
    private val _playlistsImportProgress = MutableStateFlow(ImportProgressEvent.PlaylistsProgress(false, "", 0, 0, 0))

    init {
        viewModelScope.launch {
            _playlistsImportProgress.collectLatest { 
                if (it.playlistName.isNotEmpty()) importLogs.add("Importing \"${it.playlistName}\": ${it.progressedTrackCount}/${it.totalTracksCount}") 
            }
        }
        viewModelScope.launch {
            _likedSongsImportProgress.collectLatest { 
                if (it.totalTracksCount > 0) importLogs.add("Importing Liked Songs: ${it.currentCount}/${it.totalTracksCount}")
            }
        }
    }
}
