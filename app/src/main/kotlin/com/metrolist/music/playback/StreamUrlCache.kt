/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec

internal data class CachedStreamUrl(
    val url: String,
    val requestHeaders: Map<String, String>,
    val clientName: String,
    val requireBoundedRange: Boolean = false,
    val rangeChunkSizeBytes: Long = 0L,
    val useRangeChunks: Boolean = false,
)

internal fun DataSpec.withResolvedStream(stream: CachedStreamUrl): DataSpec {
    val resolved =
        withUri(stream.url.toUri())
            .withRequestHeaders(httpRequestHeaders + stream.requestHeaders)
    if ((!stream.requireBoundedRange && !stream.useRangeChunks) || stream.rangeChunkSizeBytes <= 0L) {
        return resolved
    }
    val boundedLength =
        if (length == C.LENGTH_UNSET.toLong()) {
            stream.rangeChunkSizeBytes
        } else {
            minOf(length, stream.rangeChunkSizeBytes)
        }
    return resolved.subrange(0, boundedLength)
}

internal class StreamUrlCache(
    private val maxEntries: Int = 500,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val stream: CachedStreamUrl,
        val expiresAtMillis: Long,
    )

    /**
     * Monotonic counter per media id used to reject a stream URL that was resolved for a cache
     * generation that has since been invalidated. It is bounded like [entries] because it used to
     * be a plain `HashMap` that grew by one entry for every distinct media id ever touched and was
     * never pruned, which is a slow leak on long listening sessions.
     */
    private val generations =
        object : LinkedHashMap<String, Long>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
                size > maxEntries
        }

    private val entries =
        object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
                if (size <= maxEntries) return false
                generations.remove(eldest.key)
                return true
            }
        }

    init {
        require(maxEntries > 0) { "maxEntries must be greater than zero" }
    }

    operator fun get(mediaId: String): CachedStreamUrl? =
        synchronized(entries) {
            val entry = entries[mediaId] ?: return@synchronized null
            if (entry.expiresAtMillis <= currentTimeMillis()) {
                entries.remove(mediaId)
                advanceGeneration(mediaId)
                null
            } else {
                entry.stream
            }
        }

    fun clientName(mediaId: String): String? =
        synchronized(entries) { entries[mediaId]?.stream?.clientName }

    fun generation(mediaId: String): Long =
        synchronized(entries) { generations[mediaId] ?: 0L }

    fun put(
        mediaId: String,
        url: String,
        requestHeaders: Map<String, String>,
        clientName: String,
        expiresInSeconds: Int,
        requireBoundedRange: Boolean = false,
        rangeChunkSizeBytes: Long = 0L,
        useRangeChunks: Boolean = false,
        expectedGeneration: Long = generation(mediaId),
    ): Boolean {
        val now = currentTimeMillis()
        val ttlMillis = expiresInSeconds.coerceAtLeast(0).toLong() * 1_000L
        val expiresAtMillis =
            runCatching { Math.addExact(now, ttlMillis) }
                .getOrDefault(Long.MAX_VALUE)

        synchronized(entries) {
            if ((generations[mediaId] ?: 0L) != expectedGeneration) return false
            entries[mediaId] =
                Entry(
                    stream =
                        CachedStreamUrl(
                            url = url,
                            requestHeaders = requestHeaders.toMap(),
                            clientName = clientName,
                            requireBoundedRange = requireBoundedRange,
                            rangeChunkSizeBytes = rangeChunkSizeBytes,
                            useRangeChunks = useRangeChunks,
                        ),
                    expiresAtMillis = expiresAtMillis,
                )
            return true
        }
    }

    fun invalidate(mediaId: String) {
        synchronized(entries) {
            entries.remove(mediaId)
            advanceGeneration(mediaId)
        }
    }

    private fun advanceGeneration(mediaId: String) {
        generations[mediaId] = (generations[mediaId] ?: 0L) + 1L
    }
}
