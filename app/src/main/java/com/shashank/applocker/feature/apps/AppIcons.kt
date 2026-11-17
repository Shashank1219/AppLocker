package com.shashank.applocker.feature.apps

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache. Icons are decoded off the main thread and never written to disk. */
private object IconCache {
    private const val SIZE_PX = 96
    private val cache = LruCache<String, ImageBitmap>(150)

    suspend fun get(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(SIZE_PX, SIZE_PX).asImageBitmap()
                    .also { cache.put(packageName, it) }
            } catch (_: Exception) {
                null // Missing app or unreadable icon: the caller shows nothing.
            }
        }
    }
}

/** App icon, loaded asynchronously. Decorative: the row's text already names the app. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val icon by produceState<ImageBitmap?>(null, packageName) { value = IconCache.get(context, packageName) }
    icon?.let { Image(bitmap = it, contentDescription = null, modifier = modifier.size(40.dp)) }
}
