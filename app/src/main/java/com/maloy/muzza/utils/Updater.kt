package com.maloy.muzza.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject
import java.io.File

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set

    private const val GITHUB_API_URL = "https://api.github.com/repos/Maloy-Android/Muzza/releases/latest"

    suspend fun getLatestRelease(): Result<ReleaseInfo> = runCatching {
        val response = client.get(GITHUB_API_URL).bodyAsText()
        val json = JSONObject(response)
        val assets = json.getJSONArray("assets")
        var downloadUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                downloadUrl = asset.getString("browser_download_url")
                break
            }
        }
        
        ReleaseInfo(
            versionName = json.getString("tag_name").removePrefix("v"),
            downloadUrl = downloadUrl,
            changelog = json.getString("body")
        )
    }

    fun downloadAndInstall(context: Context, url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Actualizando ReTune")
            .setDescription("Descargando la versión más reciente...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ReTune_Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        
        // Nota: La instalación automática requiere un BroadcastReceiver que escuche 
        // a ACTION_DOWNLOAD_COMPLETE para lanzar el Intent de instalación.
    }

    data class ReleaseInfo(
        val versionName: String,
        val downloadUrl: String,
        val changelog: String
    )
}
