/**
 * ReTune Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.os.Environment
import java.io.File

object StorageUtils {
    /**
     * Returns true if a removable SD card is detected.
     */
    fun isSdCardPresent(context: Context): Boolean {
        return context.getExternalFilesDirs(null).any { it != null && Environment.isExternalStorageRemovable(it) }
    }

    /**
     * Returns a directory for storage.
     * If [useExternal] is true and an SD card is present, returns the SD card directory.
     * Otherwise, returns the internal storage directory.
     */
    fun getStorageDir(context: Context, name: String, useExternal: Boolean = false): File {
        val externalDirs = context.getExternalFilesDirs(null)
        val sdCardDir = externalDirs.firstOrNull { it != null && Environment.isExternalStorageRemovable(it) }

        val baseDir = if (useExternal && sdCardDir != null) {
            sdCardDir
        } else {
            // Default to internal "external" storage if available, otherwise internal filesDir
            externalDirs.firstOrNull() ?: context.filesDir
        }

        val targetDir = File(baseDir, name)

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        return targetDir
    }
}
