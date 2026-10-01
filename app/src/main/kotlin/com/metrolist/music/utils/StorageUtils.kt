/**
 * ReTune Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.os.Environment
import timber.log.Timber
import java.io.File

/**
 * Resolves app-private storage locations.
 *
 * Rules that keep the app safe on slow or unstable SD cards:
 *  - Authoritative data (Room database, DataStore preferences) always stays on internal storage.
 *    A removable volume can disappear at any moment; SQLite/WAL files on FAT32 media are both
 *    slow and prone to corruption when the card is yanked mid-write.
 *  - Only rebuildable data (media caches) may live on the SD card, and only when the user asked
 *    for it. Every directory is verified to be writable and falls back to internal storage
 *    when it is not, so a read-only or missing card can never crash startup.
 */
object StorageUtils {
    private const val TAG = "StorageUtils"

    /**
     * Returns the first removable, mounted and writable app-specific external directory, or `null`
     * when no usable SD card is available.
     */
    fun removableStorageDirOrNull(context: Context): File? =
        context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .filter { Environment.isExternalStorageRemovable(it) }
            .mapNotNull { dir -> mountedStateOf(dir)?.let { dir } }
            .firstOrNull { isWritableDir(it) }

    /**
     * Returns true when a removable SD card is present, mounted and writable right now.
     */
    fun isSdCardPresent(context: Context): Boolean = removableStorageDirOrNull(context) != null

    /**
     * Returns a directory for storage, creating it when needed.
     *
     * When [useExternal] is true and a usable SD card is present the directory is placed on the
     * card. In every other case — no card, unmounted card, read-only card, or a failed `mkdirs` —
     * the equivalent internal directory is returned instead, so callers always get a usable path.
     */
    fun getStorageDir(context: Context, name: String, useExternal: Boolean = false): File {
        val target = if (useExternal) {
            val removable = removableStorageDirOrNull(context)
            if (removable != null) {
                ensureDirectory(File(removable, name))
            } else {
                Timber.tag(TAG).w("Removable storage requested for '$name' but unavailable, using internal storage")
                null
            }
        } else {
            null
        } ?: ensureDirectory(File(primaryExternalDirOrInternal(context), name))

        return target
    }

    /**
     * Copies the whole content of [source] into [destination], creating [destination] when needed.
     * Existing files are left untouched, which makes repeated calls idempotent and safe to run
     * after a storage-location switch without duplicating megabytes of cache.
     *
     * Returns the number of files copied.
     */
    fun copyDirectoryContent(source: File, destination: File): Int {
        if (!source.isDirectory) return 0
        if (!destination.isDirectory && !destination.mkdirs()) {
            Timber.tag(TAG).w("Unable to create destination directory $destination")
            return 0
        }
        var copied = 0
        val children = source.listFiles() ?: return 0
        for (child in children) {
            val target = File(destination, child.name)
            if (child.isDirectory) {
                copied += copyDirectoryContent(child, target)
            } else {
                runCatching {
                    if (!target.exists() && child.length() > 0L) {
                        child.copyTo(target, overwrite = false)
                        copied++
                    }
                }.onFailure { Timber.tag(TAG).w(it, "Failed to copy ${child.name}") }
            }
        }
        return copied
    }

    /**
     * Best-effort probe that creates the directory and a scratch file inside it. Used instead of
     * [File.canWrite] because on emulated storage and some SD adapters the permission bits are
     * reported optimistically.
     */
    fun isWritableDir(dir: File): Boolean {
        if (!dir.isDirectory && !dir.mkdirs()) return false
        if (!dir.canWrite()) return false
        val probe = File(dir, ".retune_write_probe")
        return runCatching {
            probe.delete()
            probe.createNewFile().also { probe.delete() }
        }.getOrDefault(false)
    }

    private fun mountedStateOf(dir: File): String? =
        runCatching { Environment.getExternalStorageState(dir) }.getOrNull()

    private fun primaryExternalDirOrInternal(context: Context): File =
        context.getExternalFilesDirs(null).firstOrNull { it != null && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?: context.filesDir

    private fun ensureDirectory(dir: File): File {
        if (dir.isDirectory) return dir
        if (dir.mkdirs()) return dir
        Timber.tag(TAG).w("Failed to create $dir (exists=${dir.exists()}, writable=${runCatching { dir.canWrite() }.getOrDefault(false)})")
        return dir
    }
}
