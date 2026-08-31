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
     * Returns a directory for storage. Prefers SD card if detected.
     */
    fun getStorageDir(context: Context, name: String): File {
        val externalDirs = context.getExternalFilesDirs(null)
        // Look for a directory that is on a removable storage (SD card)
        val sdCardDir = externalDirs.firstOrNull { it != null && Environment.isExternalStorageRemovable(it) }
            ?: externalDirs.getOrNull(1) // Fallback to second directory if available

        val baseDir = sdCardDir ?: context.filesDir
        val targetDir = File(baseDir, name)
        
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        return targetDir
    }
}
