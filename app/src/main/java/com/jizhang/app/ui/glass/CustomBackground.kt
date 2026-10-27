package com.jizhang.app.ui.glass

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jizhang.app.util.BackgroundImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Share one bounded wallpaper bitmap across independent page and navigation compositions. */
private object BackgroundBitmapCache {
    private var key: String? = null
    private var image: ImageBitmap? = null

    @Synchronized
    fun peek(name: String?): ImageBitmap? = if (name != null && key == name) image else null

    @Synchronized
    fun load(context: Context, name: String?): ImageBitmap? {
        val file = BackgroundImageStore.resolve(context, name) ?: return null
        if (key == name) return image
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        key = name
        image = decoded
        return decoded
    }
}

@Composable
internal fun rememberCustomBackground(context: Context, name: String?): ImageBitmap? {
    val image by produceState(BackgroundBitmapCache.peek(name), context.applicationContext, name) {
        value = if (name == null) null else withContext(Dispatchers.IO) {
            BackgroundBitmapCache.load(context.applicationContext, name)
        }
    }
    return image
}
