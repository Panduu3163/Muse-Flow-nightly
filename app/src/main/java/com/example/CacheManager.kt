package com.example

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object CacheManager {
    private var simpleCache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: buildCache(context).also { simpleCache = it }
        }
    }

    private fun buildCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        // 100 MB cache limit
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(100L * 1024 * 1024)
        val databaseProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, cacheEvictor, databaseProvider)
    }
}
