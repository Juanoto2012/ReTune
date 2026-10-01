/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.metrolist.music.constants.MaxSongCacheSizeKey
import com.metrolist.music.constants.UseExternalStorageKey
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.listentogether.ListenTogetherClient
import com.metrolist.music.listentogether.ListenTogetherManager
import com.metrolist.music.utils.StorageUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao

    @Singleton
    @Provides
    fun provideInternalDatabase(
        @ApplicationContext context: Context,
    ): InternalDatabase {
        // The library database intentionally never follows the SD-card preference. A removable
        // volume can vanish mid-session, and SQLite in WAL mode on slow FAT32 media is both
        // sluggish and the most likely source of corruption. Keeping it internal preserves the
        // existing data structure and the migration chain untouched.
        val dir = StorageUtils.getStorageDir(context, "databases", useExternal = false)
        val dbFile = File(dir, InternalDatabase.DB_NAME)
        adoptLegacyExternalDatabase(context, dbFile)
        return InternalDatabase.newInternalDatabaseInstance(context, dbFile.absolutePath)
    }

    /**
     * Earlier ReTune builds stored the library database on the SD card when the user enabled
     * "use SD card". Those users still have their library there, so the very first launch after
     * this change would otherwise come up with an empty internal database.
     *
     * When — and only when — no internal database exists yet, the legacy SD copy (main file plus
     * its write-ahead log) is imported so the user keeps their library, playlists and history.
     * The `-shm` file is deliberately not copied: SQLite rebuilds it from the log.
     */
    private fun adoptLegacyExternalDatabase(
        context: Context,
        internalDbFile: File,
    ) {
        if (internalDbFile.exists()) return
        val legacyDir = runCatching { StorageUtils.getStorageDir(context, "databases", useExternal = true) }
            .getOrNull() ?: return
        val legacyDb = File(legacyDir, InternalDatabase.DB_NAME)
        if (!legacyDb.isFile || legacyDb.length() == 0L) return

        runCatching {
            internalDbFile.parentFile?.mkdirs()
            legacyDb.copyTo(internalDbFile, overwrite = false)
            // The WAL may hold transactions committed after the last checkpoint.
            File(legacyDb.path + "-wal").takeIf { it.isFile }?.copyTo(
                File(internalDbFile.path + "-wal"),
                overwrite = false,
            )
        }.onSuccess {
            Timber.tag("AppModule").i("Adopted legacy SD card library database into internal storage")
        }.onFailure {
            Timber.tag("AppModule").e(it, "Failed to adopt legacy SD card library database")
        }
    }

    @Singleton
    @Provides
    fun provideDatabase(
        internalDatabase: InternalDatabase,
    ): MusicDatabase = MusicDatabase(internalDatabase)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache {
        val useExternal = context.dataStore[UseExternalStorageKey] ?: false
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        val evictor = when (cacheSize) {
            -1 -> NoOpCacheEvictor()
            else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
        }
        return SimpleCache(
            StorageUtils.getStorageDir(context, "exoplayer", useExternal),
            evictor,
            databaseProvider,
        )
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache {
        val useExternal = context.dataStore[UseExternalStorageKey] ?: false
        return SimpleCache(
            StorageUtils.getStorageDir(context, "download", useExternal),
            NoOpCacheEvictor(),
            databaseProvider,
        )
    }
    @Singleton
    @Provides
    fun provideListenTogetherClient(
        @ApplicationContext context: Context,
    ): ListenTogetherClient = ListenTogetherClient(context)

    @Singleton
    @Provides
    fun provideListenTogetherManager(
        @ApplicationContext context: Context,
        client: ListenTogetherClient,
    ): ListenTogetherManager = ListenTogetherManager(client, context)
}
