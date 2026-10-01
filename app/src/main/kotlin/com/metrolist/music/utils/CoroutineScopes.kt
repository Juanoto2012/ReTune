/**
 * ReTune Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide IO scope for fire-and-forget work started from non-suspending code, such as the
 * `toggleLike` / `toggleLibrary` helpers on the Room entities.
 *
 * Those helpers used to allocate a brand new `CoroutineScope` on every single call. A bare
 * `CoroutineScope(Dispatchers.IO)` has no parent job, so nothing ever cancels it: every tap on a
 * like button leaked a scope and its job for as long as the request took to finish. One shared,
 * supervised scope keeps the same behaviour with a single, bounded footprint.
 */
val GlobalIoScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
