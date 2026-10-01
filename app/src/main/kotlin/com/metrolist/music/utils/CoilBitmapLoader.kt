/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import timber.log.Timber

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap = createBitmap(64, 64)

    /**
     * Copies [this] into a standalone, memory-trimmed bitmap.
     *
     * Two problems are addressed here. Artwork bytes coming off the network are frequently much
     * larger than anything the notification or lock screen needs, so they are sub-sampled down to
     * [MAX_BITMAP_DIMENSION] on the long edge instead of being copied at full resolution. And the
     * independent copy is drawn into a mutable bitmap using `RGB_565` when the source is fully
     * opaque, which roughly halves the retained size of the copy. The receiver is never recycled
     * here because callers may still hold a reference to it (Coil's memory cache does).
     */
    private fun Bitmap.createIndependentCopy(): Bitmap {
        if (isRecycled) return createFallbackBitmap()
        var scaled: Bitmap? = null
        try {
            var source = this
            val longestEdge = maxOf(source.width, source.height)
            if (longestEdge > MAX_BITMAP_DIMENSION) {
                val ratio = MAX_BITMAP_DIMENSION.toFloat() / longestEdge
                val candidate =
                    Bitmap.createScaledBitmap(
                        source,
                        (source.width * ratio).toInt().coerceAtLeast(1),
                        (source.height * ratio).toInt().coerceAtLeast(1),
                        true,
                    )
                if (candidate !== source) {
                    scaled = candidate
                    source = candidate
                }
            }
            val config = if (source.hasAlpha()) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            val copy = Bitmap.createBitmap(source.width, source.height, config)
            android.graphics.Canvas(copy).drawBitmap(source, 0f, 0f, null)
            return copy
        } catch (e: Exception) {
            Timber.tag("CoilBitmapLoader").w(e, "Failed to create independent copy")
            return createFallbackBitmap()
        } finally {
            scaled?.recycle()
        }
    }

    /**
     * Decodes [data] with sub-sampling. Artwork payloads routinely exceed several megabytes, and
     * `decodeByteArray` used to materialise all of it before it was thrown away again.
     */
    private fun decodeSampled(data: ByteArray): Bitmap {
        val bounds =
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: createFallbackBitmap()
    }

    private fun sampleSizeFor(
        width: Int,
        height: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_BITMAP_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                decodeSampled(data).createIndependentCopy()
            } catch (e: Exception) {
                Timber.tag("CoilBitmapLoader").w(e, "Failed to decode bitmap data")
                createFallbackBitmap()
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(uri)
                        .size(MAX_BITMAP_DIMENSION)
                        .allowHardware(false)
                        .build()

                when (val result = context.imageLoader.execute(request)) {
                    is ErrorResult -> {
                        createFallbackBitmap()
                    }

                    is SuccessResult -> {
                        try {
                            val bitmap = result.image.toBitmap()
                            bitmap.createIndependentCopy()
                        } catch (e: Exception) {
                            Timber.tag("CoilBitmapLoader").w(e, "Failed to convert image to bitmap")
                            createFallbackBitmap()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("CoilBitmapLoader").w(e, "Failed to load bitmap from uri")
                createFallbackBitmap()
            }
        }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        metadata.artworkData?.let { return decodeBitmap(it) }
        val artworkUri = metadata.artworkUri ?: metadata.extras?.getString("artwork_uri")?.toUri() ?: return null
        return loadBitmap(artworkUri)
    }

    private companion object {
        /** Artwork beyond this on the long edge is invisible in notifications and wastes heap. */
        const val MAX_BITMAP_DIMENSION = 1024
    }
}
