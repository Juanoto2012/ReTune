package com.maloy.muzza.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.db.entities.PlaylistSongMap
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.ImportFromSpotifyScreenState
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.ImportProgressEvent
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.Playlist
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.ui.utils.toHighResThumbnail
import com.maloy.muzza.utils.SpotifyHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class ImportFromSpotifyViewModel @Inject constructor(
    private val localDatabase: MusicDatabase
) : ViewModel() {

    private fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(remastered.*?\\)"), "")
            .replace(Regex("(?i)\\[remastered.*?\\]"), "")
            .replace(Regex("(?i)- remastered.*?$"), "")
            .replace(Regex("(?i)\\(live.*?\\)"), "")
            .replace(Regex("(?i)- live.*?$"), "")
            .replace(Regex("(?i)\\[official.*?\\]"), "")
            .replace(Regex("(?i)\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\(feat\\..*?\\)"), "")
            .replace(Regex("(?i)\\[feat\\..*?\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

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

    fun loginWithCredentials(clientId: String, clientSecret: String, code: String) {
        viewModelScope.launch {
            try {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true, error = false)
                val token = SpotifyHelper.getAccessTokenWithCredentials(clientId, clientSecret, code)
                fetchInitialData(token)
            } catch (e: Exception) { handleError(e) }
        }
    }

    fun fetchPlaylistsWithSpDc(spDc: String) {
        viewModelScope.launch {
            try {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true, error = false)
                val token = SpotifyHelper.getAccessTokenWithCookie(spDc)
                fetchInitialData(token)
            } catch (e: Exception) { handleError(e) }
        }
    }

    private suspend fun fetchInitialData(token: String) {
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            accessToken = token,
            isObtainingAccessTokenSuccessful = true
        )

        val profile = SpotifyHelper.getUserProfile(token)
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            userName = profile.display_name ?: "Spotify User"
        )

        val response = SpotifyHelper.getPlaylists(token)
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            playlists = response.items.map { 
                com.maloy.muzza.models.spotify.playlists.SpotifyPlaylistItem(
                    playlistDescription = "",
                    playlistId = it.id,
                    playlistName = it.name,
                    images = it.images.map { img -> com.maloy.muzza.models.spotify.playlists.Images(img.url) },
                    tracks = com.maloy.muzza.models.spotify.Tracks(it.tracks?.total ?: 0),
                    type = "playlist",
                    uri = "spotify:playlist:${it.id}"
                )
            },
            totalPlaylistsCount = response.total ?: 0,
            reachedEndForPlaylistPagination = response.next == null,
            isRequesting = false
        )
    }

    private fun handleError(e: Throwable) {
        Timber.e(e)
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            isRequesting = false,
            error = true,
            exception = if (e is Exception) e else Exception(e.message)
        )
    }

    private var playListPaginationOffset = 0

    fun retrieveNextPageOfPlaylists() {
        viewModelScope.launch {
            val token = importFromSpotifyScreenState.value.accessToken
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true)
            
            playListPaginationOffset += 50
            try {
                val response = SpotifyHelper.getPlaylists(token, offset = playListPaginationOffset)
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    playlists = importFromSpotifyScreenState.value.playlists + response.items.map {
                        com.maloy.muzza.models.spotify.playlists.SpotifyPlaylistItem(
                            playlistDescription = "",
                            playlistId = it.id,
                            playlistName = it.name,
                            images = it.images.map { img -> com.maloy.muzza.models.spotify.playlists.Images(img.url) },
                            tracks = com.maloy.muzza.models.spotify.Tracks(it.tracks?.total ?: 0),
                            type = "playlist",
                            uri = "spotify:playlist:${it.id}"
                        )
                    },
                    totalPlaylistsCount = response.total ?: 0,
                    reachedEndForPlaylistPagination = response.next == null,
                    isRequesting = false
                )
            } catch (e: Exception) {
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
                if (saveInDefaultLikedSongs != null) {
                    importSpotifyLikedSongs(saveInDefaultLikedSongs)
                }
                importPlaylists(selectedPlaylists, importFromSpotifyScreenState.value.accessToken)
                
                withContext(Dispatchers.Main) {
                    isImportingCompleted.value = true
                    isImportingInProgress.value = false
                }
            }
        }
    }

    private suspend fun importPlaylists(selectedPlaylists: List<Playlist>, authToken: String) {
        selectedPlaylists.forEachIndexed { playlistIndex, playlist ->
            var progressedTracksCount = 0
            val generatedPlaylistId = PlaylistEntity.generatePlaylistId()
            
            localDatabase.insert(PlaylistEntity(
                id = generatedPlaylistId, 
                name = playlist.name,
                bookmarkedAt = LocalDateTime.now()
            ))
            
            val response = SpotifyHelper.getPlaylistTracks(authToken, playlist.id)
            val tracks = response.items.mapNotNull { it.track }

            tracks.forEach { track ->
                val cleanTitle = sanitizeTitle(track.name ?: "Unknown")
                val artistName = track.artists.firstOrNull()?.name ?: ""
                val query = "$cleanTitle $artistName"
                
                val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val song = searchResult?.items?.firstOrNull() as? SongItem
                
                if (song != null) {
                    localDatabase.transaction {
                        insert(song.toMediaMetadata()) {
                            it.copy(inLibrary = LocalDateTime.now())
                        }
                        insert(PlaylistSongMap(playlistId = generatedPlaylistId, songId = song.id))
                    }
                }
                
                _playlistsImportProgress.emit(ImportProgressEvent.PlaylistsProgress(
                    completed = false,
                    progressedTrackCount = ++progressedTracksCount,
                    playlistName = playlist.name,
                    totalTracksCount = tracks.size,
                    currentPlaylistIndex = playlistIndex
                ))
                delay(100)
            }
        }
    }

    private suspend fun importSpotifyLikedSongs(saveInDefaultLikedSongs: Boolean) {
        val progressedTracks = AtomicInteger(0)
        val token = try { SpotifyHelper.fetchAnonymousToken() } catch (e: Exception) { importFromSpotifyScreenState.value.accessToken }
        
        try {
            val response = SpotifyHelper.getLikedSongs(token)
            val totalSongsCount = response.total ?: 0

            response.items.mapNotNull { it.track }.forEach { likedSong ->
                val cleanTitle = sanitizeTitle(likedSong.name ?: "Unknown")
                val artistName = likedSong.artists.firstOrNull()?.name ?: ""
                val query = "$cleanTitle $artistName"
                
                val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val song = searchResult?.items?.firstOrNull() as? SongItem
                
                if (song != null) {
                    localDatabase.transaction {
                        insert(song.toMediaMetadata()) {
                            it.copy(
                                liked = saveInDefaultLikedSongs,
                                inLibrary = if (saveInDefaultLikedSongs) LocalDateTime.now() else it.inLibrary
                            )
                        }
                    }
                }
                
                _likedSongsImportProgress.emit(ImportProgressEvent.LikedSongsProgress(
                    completed = false,
                    currentCount = progressedTracks.incrementAndGet(),
                    totalTracksCount = totalSongsCount
                ))
                delay(100)
            }
        } catch (e: Exception) { Timber.e(e) }
    }

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
