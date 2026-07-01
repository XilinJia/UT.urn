package ac.stresa.uturn.core.util

import ac.mdiq.podcini.shared.nowInMillis
import android.util.Log
import androidx.collection.LruCache
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.ServiceList
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class InfoCache private constructor() {
    private val TAG: String = javaClass.getSimpleName()

    enum class Type {
        STREAM,
        CHANNEL,
        CHANNEL_TAB,
        COMMENTS,
        PLAYLIST,
        KIOSK,
    }

    fun getFromKey(serviceId: Int, url: String, cacheType: Type): Info? {
        Log.d(TAG, ("getFromKey() called with: serviceId = [$serviceId], url = [$url]"))
        synchronized(LRU_CACHE) { return getInfo(keyOf(serviceId, url, cacheType)) }
    }

    fun putInfo(serviceId: Int, url: String, info: Info, cacheType: Type) {
        Log.d(TAG, "putInfo() called with: info = [$info]")

        val expirationMillis = getCacheExpirationMillis(info.serviceId)
        synchronized(LRU_CACHE) {
            val data = CacheData(info, expirationMillis)
            LRU_CACHE.put(keyOf(serviceId, url, cacheType), data)
        }
    }

    private fun getCacheExpirationMillis(serviceId: Int): Long {
        return if (serviceId == ServiceList.SoundCloud.serviceId) 5.minutes.inWholeMilliseconds
        else 1.hours.inWholeMilliseconds
    }

    fun removeInfo(serviceId: Int, url: String, cacheType: Type) {
        Log.d(TAG, ("removeInfo() called with: serviceId = [$serviceId], url = [$url]"))
        synchronized(LRU_CACHE) { LRU_CACHE.remove(keyOf(serviceId, url, cacheType)) }
    }

    fun clearCache() {
        Log.d(TAG, "clearCache() called")
        synchronized(LRU_CACHE) { LRU_CACHE.evictAll() }
    }

    fun trimCache() {
        Log.d(TAG, "trimCache() called")
        synchronized(LRU_CACHE) {
            removeStaleCache()
            LRU_CACHE.trimToSize(TRIM_CACHE_TO)
        }
    }

    val size: Long
        get() = synchronized(LRU_CACHE) { return LRU_CACHE.size().toLong() }

    private class CacheData(val info: Info, timeoutMillis: Long) {
        private val expireTimestamp: Long = nowInMillis() + timeoutMillis

        val isExpired: Boolean
            get() = nowInMillis() > expireTimestamp
    }

    companion object {
        val instance: InfoCache = InfoCache()
        private const val MAX_ITEMS_ON_CACHE = 60

        private const val TRIM_CACHE_TO = 30

        private val LRU_CACHE = LruCache<String, CacheData>(MAX_ITEMS_ON_CACHE)

        private fun keyOf(serviceId: Int, url: String, cacheType: Type): String {
            return serviceId.toString() + ":" + cacheType.ordinal + ":" + url
        }

        private fun removeStaleCache() {
            for (entry in LRU_CACHE.snapshot().entries) {
                val data = entry.value
                if (data.isExpired) LRU_CACHE.remove(entry.key)
            }
        }

        private fun getInfo(key: String): Info? {
            val data = LRU_CACHE[key] ?: return null
            if (data.isExpired) {
                LRU_CACHE.remove(key)
                return null
            }
            return data.info
        }
    }
}
