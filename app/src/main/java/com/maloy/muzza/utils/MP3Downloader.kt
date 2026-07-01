package com.maloy.muzza.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import com.maloy.muzza.R
import com.maloy.muzza.constants.AudioQuality
import com.maloy.muzza.constants.AudioQualityKey
import com.maloy.muzza.constants.DownloadFolderKey
import com.maloy.muzza.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object MP3Downloader {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) rv:128.0) Gecko/20100101 Firefox/128.0"

    suspend fun downloadSongAsMP3(
        context: Context,
        videoId: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        thumbnailUrl: String? = null
    ) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.downloading) + ": $title", Toast.LENGTH_SHORT).show()
        }

        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        val audioQuality = context.dataStore[AudioQualityKey].toEnum(AudioQuality.AUTO)
        val downloadFolderUriString = context.dataStore[DownloadFolderKey] ?: ""

        if (downloadFolderUriString.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please select a download folder in Storage Settings", Toast.LENGTH_LONG).show()
            }
            return
        }

        val downloadFolderUri = Uri.parse(downloadFolderUriString)
        val directory = DocumentFile.fromTreeUri(context, downloadFolderUri)
        if (directory == null || !directory.canWrite()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Cannot write to the selected folder", Toast.LENGTH_LONG).show()
            }
            return
        }

        val playbackDataResult = YTPlayerUtils.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager
        )

        val playbackData = playbackDataResult.getOrNull()
        if (playbackData == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to get download link", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val streamUrl = playbackData.streamUrl
        val mimeType = playbackData.format.mimeType ?: "audio/mp4"
        val isWebm = mimeType.contains("webm")
        val extension = if (isWebm) ".webm" else ".m4a"
        val finalFileName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_") + extension

        // Create temp files
        val cacheDir = context.cacheDir
        val tempInFile = File(cacheDir, "temp_in_$videoId$extension")
        val tempThumbFile = File(cacheDir, "temp_thumb_$videoId.jpg")

        try {
            withContext(Dispatchers.IO) {
                // 1. Download Stream
                downloadToFile(streamUrl, tempInFile)

                // 2. Download Thumbnail if available
                if (thumbnailUrl != null) {
                    try {
                        downloadToFile(thumbnailUrl, tempThumbFile)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to download thumbnail")
                    }
                }

                // 3. Tagging with JAudioTagger
                try {
                    val audioFile = AudioFileIO.read(tempInFile)
                    val tag = audioFile.tagOrCreateDefault
                    tag.setField(FieldKey.TITLE, title)
                    artist?.let { tag.setField(FieldKey.ARTIST, it) }
                    album?.let { tag.setField(FieldKey.ALBUM, it) }
                    
                    if (tempThumbFile.exists()) {
                        val artwork = ArtworkFactory.createArtworkFromFile(tempThumbFile)
                        tag.setField(artwork)
                    }
                    
                    audioFile.commit()
                } catch (e: Exception) {
                    Timber.e(e, "Tagging failed for $title")
                }

                // 4. Save to final destination
                val file = directory.createFile(if (isWebm) "audio/webm" else "audio/mp4", finalFileName)
                if (file != null) {
                    context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                        tempInFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } else {
                    throw Exception("Failed to create file in destination")
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Downloaded: $title", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Download failed for $title")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            tempInFile.delete()
            tempThumbFile.delete()
        }
    }

    private fun downloadToFile(url: String, file: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.youtube.com/")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("Empty body")
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
