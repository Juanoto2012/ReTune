/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.EnableSongCacheKey
import com.metrolist.music.constants.MaxImageCacheSizeKey
import com.metrolist.music.constants.MaxSongCacheSizeKey
import com.metrolist.music.constants.UseExternalStorageKey
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import android.text.format.Formatter
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.StorageUtils
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import kotlin.math.roundToInt

/** Cache directories that follow the "use SD card" preference and must be carried over on a switch. */
private val MOVABLE_CACHE_DIRS = listOf("exoplayer", "download", "coil")

/** Walking a large cache directory on a slow SD card is expensive, so poll sparingly. */
private const val CACHE_SIZE_POLL_INTERVAL_MS = 2000L

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class, DelicateCoilApi::class)
@Composable
fun StorageSettings(
    navController: NavController
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return

    val coroutineScope = rememberCoroutineScope()
    val songCacheString = stringResource(R.string.song_cache).lowercase()
    val imageCacheString = stringResource(R.string.image_cache).lowercase()
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
        key = MaxSongCacheSizeKey,
        defaultValue = 1024
    )
    val (enableSongCache, onEnableSongCacheChange) = rememberPreference(
        key = EnableSongCacheKey,
        defaultValue = true
    )
    val (useExternalStorage, onUseExternalStorageChange) = rememberPreference(
        key = UseExternalStorageKey,
        defaultValue = false
    )

    var clearDownloads by remember { mutableStateOf(false) }
    var clearCacheDialog by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }

    // SD card presence is re-evaluated instead of being frozen at composition time, so inserting
    // or removing the card is picked up without restarting the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    var sdCardAvailable by remember { mutableStateOf(StorageUtils.isSdCardPresent(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                    sdCardAvailable = StorageUtils.isSdCardPresent(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // State for the confirmation dialog
    var showCacheWarningDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var cacheType by remember { mutableStateOf("") }
    var cacheUsage by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var onConfirmAction by remember { mutableStateOf<() -> Unit>({}) }

    var imageCacheSize by remember {
        androidx.compose.runtime.mutableLongStateOf(imageDiskCache.size)
    }
    var playerCacheSize by remember {
        androidx.compose.runtime.mutableLongStateOf(tryOrNull { playerCache.cacheSpace } ?: 0)
    }
    var downloadCacheSize by remember {
        mutableLongStateOf(tryOrNull { downloadCache.cacheSpace } ?: 0)
    }
    val imageCacheProgress by animateFloatAsState(
        targetValue =
            (imageCacheSize.toFloat() / (maxImageCacheSize * 1024 * 1024L)).coerceIn(
                0f,
                1f,
            ),
        label = "imageCacheProgress",
    )
    val playerCacheProgress by animateFloatAsState(
        targetValue =
            (playerCacheSize.toFloat() / (maxSongCacheSize * 1024 * 1024L)).coerceIn(
                0f,
                1f,
            ),
        label = "playerCacheProgress",
    )

    LaunchedEffect(maxImageCacheSize) {
        SingletonImageLoader.reset()
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache.clear()
            }
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                playerCache.keys.forEach { key ->
                    playerCache.removeResource(key)
                }
            }
        }
    }

    // Cache sizes are polled while the screen is resumed, on an IO dispatcher, and on a slow
    // interval. Walking a multi-gigabyte cache on a slow SD card is expensive, so this used to
    // stall the UI three times per second.
    LaunchedEffect(lifecycleOwner, imageDiskCache) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(CACHE_SIZE_POLL_INTERVAL_MS)
                imageCacheSize = withContext(Dispatchers.IO) { imageDiskCache.size }
            }
        }
    }
    LaunchedEffect(lifecycleOwner, playerCache) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(CACHE_SIZE_POLL_INTERVAL_MS)
                playerCacheSize = withContext(Dispatchers.IO) { tryOrNull { playerCache.cacheSpace } ?: 0L }
            }
        }
    }
    LaunchedEffect(lifecycleOwner, downloadCache) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(CACHE_SIZE_POLL_INTERVAL_MS)
                downloadCacheSize = withContext(Dispatchers.IO) { tryOrNull { downloadCache.cacheSpace } ?: 0L }
            }
        }
    }

    if (clearDownloads) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_all_downloads),
            onDismiss = { clearDownloads = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    downloadCache.keys.forEach { key ->
                        downloadCache.removeResource(key)
                    }
                }
                clearDownloads = false
            },
            onCancel = { clearDownloads = false },
            content = {
                Text(text = stringResource(R.string.clear_downloads_dialog))
            },
        )
    }
    if (clearCacheDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_song_cache),
            onDismiss = { clearCacheDialog = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    playerCache.keys.forEach { key ->
                        playerCache.removeResource(key)
                    }
                }
                clearCacheDialog = false
            },
            onCancel = { clearCacheDialog = false },
            content = {
                Text(text = stringResource(R.string.clear_song_cache_dialog))
            },
        )
    }
    if (clearImageCacheDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_image_cache),
            onDismiss = { clearImageCacheDialog = false },
            onConfirm = {
                coroutineScope.launch(Dispatchers.IO) {
                    val urlsToPreserve = mutableSetOf<String>()
                    val downloadedSongs =
                        try {
                            database.downloadedSongsByNameAsc().first()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    downloadedSongs.forEach { song ->
                        song.song.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                        song.album?.thumbnailUrl?.let { urlsToPreserve.add(it.encodeUtf8().sha256().hex()) }
                    }
                    val directory = imageDiskCache.directory.toFile()
                    if (directory.exists() && directory.isDirectory) {
                        directory.listFiles()?.forEach { file ->
                            if (file.isFile && !file.name.startsWith("journal")) {
                                val isPreserved = urlsToPreserve.any { hash -> file.name.startsWith(hash) }
                                if (!isPreserved) {
                                    file.delete()
                                }
                            }
                        }
                    }
                    imageDiskCache.clear()
                }
                clearImageCacheDialog = false
            },
            onCancel = { clearImageCacheDialog = false },
            content = {
                Text(text = stringResource(R.string.clear_image_cache_dialog))
            },
        )
    }

    // Confirmation Dialog
    if (showCacheWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCacheWarningDialog = false },
            title = { Text(stringResource(R.string.cache_size_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cache_size_warning_message,
                        Formatter.formatShortFileSize(context, cacheUsage),
                        cacheType,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmAction()
                        showCacheWarningDialog = false
                    },
                ) {
                    Text(
                        stringResource(R.string.cache_size_warning_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheWarningDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.restart_required)) },
            text = { Text(stringResource(R.string.storage_restart_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    },
                ) {
                    Text(stringResource(R.string.restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    // Moving the cache between internal storage and the SD card used to simply relocate the
    // directory, which silently "lost" every download and song cache entry. The old content is
    // copied over first, so switching back and forth is lossless.
    fun requestStorageLocation(useSdCard: Boolean) {
        if (useSdCard && !StorageUtils.isSdCardPresent(context)) return
        val previousExternal = useExternalStorage
        if (previousExternal != useSdCard) {
            coroutineScope.launch(Dispatchers.IO) {
                MOVABLE_CACHE_DIRS.forEach { name ->
                    val source = StorageUtils.getStorageDir(context, name, useExternal = previousExternal)
                    val destination = StorageUtils.getStorageDir(context, name, useExternal = useSdCard)
                    if (source.absolutePath != destination.absolutePath) {
                        StorageUtils.copyDirectoryContent(source, destination)
                    }
                }
            }
        }
        onUseExternalStorageChange(useSdCard)
        showRestartDialog = true
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )
        Material3SettingsGroup(
            title = stringResource(R.string.storage),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = { Text(stringResource(R.string.storage_location)) },
                        description = {
                            Text(
                                if (sdCardAvailable) {
                                    stringResource(R.string.use_sd_card_desc)
                                } else {
                                    stringResource(R.string.sd_card_not_detected)
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = useExternalStorage,
                                enabled = sdCardAvailable,
                                onCheckedChange = { requestStorageLocation(it) },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (useExternalStorage) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { requestStorageLocation(!useExternalStorage) }
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = { Text(stringResource(R.string.downloaded_songs)) },
                        description = {
                            Text(text = Formatter.formatShortFileSize(context, downloadCacheSize))
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.clear_all_downloads)) },
                        onClick = {
                            clearDownloads = true
                        },
                    ),
                ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.song_cache),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.cached),
                    title = { Text(stringResource(R.string.enable_song_cache)) },
                    description = { Text(stringResource(R.string.enable_song_cache_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableSongCache,
                            onCheckedChange = onEnableSongCacheChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableSongCache) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableSongCacheChange(!enableSongCache) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.cached),
                    title = { Text(stringResource(R.string.max_song_cache_size)) },
                    enabled = enableSongCache,
                    description = {
                        val songCacheValues =
                            remember { listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1) }
                        Column {
                            Text(
                                text = when (maxSongCacheSize) {
                                    0 -> stringResource(R.string.disable)
                                    -1 -> stringResource(R.string.unlimited)
                                    else -> Formatter.formatShortFileSize(context, maxSongCacheSize * 1024 * 1024L)
                                }
                            )
                            Slider(
                                value = songCacheValues.indexOf(maxSongCacheSize).toFloat(),
                                enabled = enableSongCache,
                                onValueChange = {
                                    val newValue = songCacheValues[it.roundToInt()]
                                    val newLimitInBytes = if (newValue == -1) {
                                        Long.MAX_VALUE
                                    } else {
                                        newValue * 1024 * 1024L
                                    }

                                        if (newLimitInBytes < playerCacheSize) {
                                            cacheUsage = playerCacheSize
                                            cacheType = songCacheString
                                            onConfirmAction = { onMaxSongCacheSizeChange(newValue) }
                                            showCacheWarningDialog = true
                                        } else {
                                            onMaxSongCacheSizeChange(newValue)
                                        }
                                    },
                                    steps = songCacheValues.size - 2,
                                    valueRange = 0f..(songCacheValues.size - 1).toFloat(),
                                )
                                LinearProgressIndicator(
                                    progress = { playerCacheProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text(
                                    text =
                                        if (maxSongCacheSize == -1) {
                                            Formatter.formatShortFileSize(context, playerCacheSize)
                                        } else {
                                            "${Formatter.formatShortFileSize(context, playerCacheSize)} / ${
                                                Formatter.formatShortFileSize(context, 
                                                    maxSongCacheSize * 1024 * 1024L,
                                                )
                                            }"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.clear_song_cache)) },
                        onClick = {
                            clearCacheDialog = true
                        },
                    ),
                ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.image_cache),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.manage_search),
                        title = { Text(stringResource(R.string.max_image_cache_size)) },
                        description = {
                            val imageCacheValues =
                                remember { listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192) }
                            Column {
                                Text(
                                    text =
                                        when (maxImageCacheSize) {
                                            0 -> stringResource(R.string.disable)
                                            else -> Formatter.formatShortFileSize(context, maxImageCacheSize * 1024 * 1024L)
                                        },
                                )
                                Slider(
                                    value = imageCacheValues.indexOf(maxImageCacheSize).toFloat(),
                                    onValueChange = {
                                        val newValue = imageCacheValues[it.roundToInt()]
                                        val newLimitInBytes = newValue * 1024 * 1024L

                                        if (newLimitInBytes < imageCacheSize) {
                                            cacheUsage = imageCacheSize
                                            cacheType = imageCacheString
                                            onConfirmAction = { onMaxImageCacheSizeChange(newValue) }
                                            showCacheWarningDialog = true
                                        } else {
                                            onMaxImageCacheSizeChange(newValue)
                                        }
                                    },
                                    steps = imageCacheValues.size - 2,
                                    valueRange = 0f..(imageCacheValues.size - 1).toFloat(),
                                )
                                LinearProgressIndicator(
                                    progress = { imageCacheProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    strokeCap = StrokeCap.Round,
                                )
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text(
                                    text = "${Formatter.formatShortFileSize(context, imageCacheSize)} / ${
                                        Formatter.formatShortFileSize(context, 
                                            maxImageCacheSize * 1024 * 1024L,
                                        )
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.clear_image_cache)) },
                        onClick = {
                            clearImageCacheDialog = true
                        },
                    ),
                ),
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.storage)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
