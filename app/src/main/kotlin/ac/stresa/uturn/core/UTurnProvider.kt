package ac.stresa.uturn.core

import ac.mdiq.podcini.shared.AudioSpec
import ac.mdiq.podcini.shared.EpisodeIPC
import ac.mdiq.podcini.shared.FeedIPC
import ac.mdiq.podcini.shared.VideoSpec
import ac.mdiq.podcini.shared.prepareUrl
import ac.mdiq.podcini.sources.Provider
import ac.stresa.uturn.core.FeedBuilder.Companion.episodeFrom
import ac.stresa.uturn.core.util.InfoCache
import android.service.autofill.UserData
import android.util.Log
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class UTurnProvider: Provider.Stub() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val ongoingRequests = ConcurrentHashMap<String, Job>()

    private val serviceId = 0
    private val npService by lazy { NewPipe.getService(serviceId) }


    fun loadUserData(userId: String): UserData? {
        val requestKey = "loadUserData_$userId"
        val deferredResult = CompletableDeferred<UserData?>()
        ongoingRequests[requestKey] = deferredResult

        val job = serviceScope.launch {
            try {
                deferredResult.complete(fetchUserFromNetwork(userId))
            } catch (e: Exception) {
                deferredResult.completeExceptionally(e)
            }
        }

        deferredResult.invokeOnCompletion {
            if (deferredResult.isCancelled) job.cancel()
            ongoingRequests.remove(requestKey)
        }

        return runBlocking {
            try { deferredResult.await() } catch (e: CancellationException) { null }
        }
    }

    suspend fun fetchUserFromNetwork(userId: String): UserData? {
        return null
    }

    fun cancelOperation(operationPrefix: String, id: String) {
        val requestKey = "${operationPrefix}_$id"
        ongoingRequests[requestKey]?.cancel()
    }

    private val CACHE: InfoCache = InfoCache.instance

    override fun canHandleUrl(url_: String): Int {
        val url = try { URL(url_) } catch (e: Exception) { return -1 }
        return if ((YoutubeParsingHelper.isYoutubeURL(url) && (url.path.startsWith("/watch") || url.path.startsWith("/live"))) || YoutubeParsingHelper.isYoutubeServiceURL(url)) 1 else -1
    }

    override fun buildEpisode(url: String): EpisodeIPC? {
        val info = StreamInfo.getInfo(npService, url)
        return if (info != null) episodeFrom(info) else null
    }

    override fun getEpisodeDescription(url: String): String? {
        return getStreamInfo(url)?.description?.content
    }

    override fun getAudioSpecs(media: EpisodeIPC): List<AudioSpec> {
        var sSpecs = listOf<AudioSpec>()
        val audioStreams = getStreamInfo(media.downloadUrl)?.audioStreams
        if (audioStreams != null) {
            val collectedStreams = mutableSetOf<AudioSpec>()
            for (stream in audioStreams) {
                //                Log.d(TAG, "getFilteredAStreams stream: ${stream.audioTrackId} ${stream.bitrate} ${stream.deliveryMethod} ${stream.format}")
                if (stream == null || stream.deliveryMethod == DeliveryMethod.TORRENT || (stream.deliveryMethod == DeliveryMethod.HLS && stream.format == MediaFormat.OPUS)) continue
                collectedStreams.add(toAudioSpec(stream))
            }
            sSpecs = collectedStreams.toList().sortedWith(compareBy { it.bitrate })
        }
        return sSpecs
    }

    override fun getVideoOnlySpecs(media: EpisodeIPC): List<VideoSpec> {
        return getSortedVStreams(listOf(), getStreamInfo(media.downloadUrl)?.videoOnlyStreams, ascendingOrder = true, preferVideoOnlyStreams = true)
    }

    override fun getVideoSpecs(media: EpisodeIPC):  List<VideoSpec> {
        return getSortedVStreams(getStreamInfo(media.downloadUrl)?.videoStreams, listOf(), ascendingOrder = true, preferVideoOnlyStreams = false)
    }

    internal fun getStreamInfo(url: String?, forceLoad: Boolean = false): StreamInfo? {
        if (url.isNullOrBlank()) return null
        val cacheType = InfoCache.Type.STREAM
        val streamInfo = StreamInfo.getInfo(npService, url).also { info -> CACHE.putInfo(serviceId, url, info, cacheType) }
        return if (forceLoad) {
            CACHE.removeInfo(serviceId, url, cacheType)
            streamInfo
        } else {
            val cachedData = CACHE.getFromKey(serviceId, url, cacheType) as? StreamInfo
            cachedData ?: streamInfo
        }
    }

    internal fun String.toResolutionValue(): Int {
        val match = Regex("(\\d+)p|(\\d+)k").find(this)
        return when {
            match?.groupValues?.get(1) != null -> match.groupValues[1].toInt()
            match?.groupValues?.get(2) != null -> match.groupValues[2].toInt() * 1024
            else -> 0
        }
    }

    internal fun getSortedVStreams(videoStreams: List<VideoStream>?, videoOnlyStreams: List<VideoStream>?, ascendingOrder: Boolean, preferVideoOnlyStreams: Boolean): List<VideoSpec> {
        val videoStreamsOrdered = if (preferVideoOnlyStreams) listOf(videoStreams, videoOnlyStreams) else listOf(videoOnlyStreams, videoStreams)
        val allInitialStreams = videoStreamsOrdered.filterNotNull().flatten().toList()
        val comparator = compareBy<VideoStream> { it.getResolution().toResolutionValue() }
        val vList = mutableListOf<VideoSpec>()
        (if (ascendingOrder) allInitialStreams.sortedWith(comparator) else allInitialStreams.sortedWith(comparator.reversed())).forEach { vList.add(toVideoSpec(it)) }
        return vList
    }

    private fun toAudioSpec(s: AudioStream): AudioSpec {
        val a = AudioSpec()
        a.averageBitrate = s.averageBitrate
        a.bitrate = s.bitrate
        a.quality = s.quality
        a.codec = s.codec
        a.format = s.format?.name
        a.audioTrackId = s.audioTrackId
        a.audioTrackName = s.audioTrackName
        a.audioLocale = s.audioLocale?.toLanguageTag()
        a.deliveryMethod = s.deliveryMethod.name
        a.url = if (s.isUrl) s.content else {
            Log.e(TAG, "AudioStream content is not url: ${s.content}")
            null
        }
        return a
    }

    private fun toVideoSpec(s: VideoStream): VideoSpec {
        val v = VideoSpec()
        v.isVideoOnly = s.isVideoOnly()
        v.bitrate = s.bitrate
        v.fps = s.fps
        v.width = s.width
        v.height = s.height
        v.quality = s.quality
        v.codec = s.codec
        v.deliveryMethod = s.deliveryMethod.name
        v.resolution = s.getResolution()
        v.url = if (s.isUrl) s.content else {
            Log.e(TAG, "VideoStream content is not url: ${s.content}")
            null
        }
        return v
    }

    private fun isYTChannel(url: String): Boolean {
        try {
            val uURL =  Url(url)
            return uURL.encodedPath.startsWith("/channel") || uURL.encodedPath.startsWith("/@")
        } catch (e: Exception) {
            Log.e(TAG, "isYTChannel urlInit is not valid $url")
            return false
        }
    }

    private fun isYTPlaylist(url: String): Boolean {
        try { return Url(url).encodedPath.startsWith("/playlist")
        } catch (e: Exception) {
            Log.e(TAG, "isYTChannel urlInit is not valid $url")
            return false
        }
    }

    private var fb: FeedBuilder? = null

    override fun buildFeed(url: String, index: Int): FeedIPC? {
        fb = FeedBuilder(url)
        return runBlocking(Dispatchers.IO) {
            when {
                isYTChannel(url) -> {
                    fb?.channelInfo = ChannelInfo.getInfo(npService, url)
                    fb?.buildYTChannel(index, "")
                }
                isYTPlaylist(url) -> if (index == 0) fb?.buildYTPlaylist() else null
                else -> null
            }
        }
    }

    override fun getEpisodes(total: Int, since: Long): List<EpisodeIPC> {
        if (fb == null) return listOf()
        return runBlocking(Dispatchers.IO) {
            when {
                isYTChannel(fb!!.urlInit) -> fb!!.episodesFromChannel(total, since)
                isYTPlaylist(fb!!.urlInit) -> fb!!.episodesFromList(total, since)
                else -> listOf()
            }
        }
    }

    override fun feedToUpdate(url: String): FeedIPC? {
        var feed_: FeedIPC?
        when {
            isYTChannel(url) -> {
                fb = FeedBuilder(url)
                fb?.channelInfo = ChannelInfo.getInfo(npService, url)
                Log.d(TAG, "feedToUpdate channelInfo: ${fb?.channelInfo} ${fb?.channelInfo?.tabs?.size}")
                runBlocking(Dispatchers.IO) { feed_ = fb?.buildYTChannel(0, "") }
            }
            isYTPlaylist(url) -> runBlocking(Dispatchers.IO) {
                fb = FeedBuilder(url)
                feed_ = fb?.buildYTPlaylist()
            }
            else -> {
                // channel tabs other than videos
                Log.d(TAG, "feedToUpdate url: $url")
                val uURL =  Url(url)
                val pathSegments = uURL.encodedPath.split("/")
                val channelUrl = "https://www.youtube.com/channel/${pathSegments[1]}"
                Log.d(TAG, "feedToUpdate channelUrl: $channelUrl")
                val channelInfo = ChannelInfo.getInfo(npService, channelUrl)
                fb = FeedBuilder(channelUrl)
                fb?.channelInfo = channelInfo
                Log.d(TAG, "feedToUpdate channelInfo: $channelInfo ${channelInfo.tabs.size}")
                if (channelInfo?.tabs.isNullOrEmpty()) return null
                var index = -1
                var urlEnd = ""
                for (i in channelInfo.tabs.indices) {
                    urlEnd = Url(channelInfo.tabs[i].url).encodedPath.split("/").last()
                    val url_ = prepareUrl(channelInfo.tabs[i].url)
                    Log.d(TAG, "feedToUpdate url_: $url_")
                    if (url == url_) {
                        index = i
                        break
                    }
                }
                if (index < 0) return null
                runBlocking(Dispatchers.IO) {
                    feed_ = fb?.buildYTChannel(index, "")
                    if (feed_ != null && urlEnd.isNotBlank()) feed_.title = "${feed_.title}: $urlEnd"
                }
            }
        }
        feed_?.id = 0L
        return feed_
    }

    override fun feedsTitlesAtUrl(url_: String): List<String> {
        if (!isYTChannel(url_)) return listOf()
        val channelInfo = ChannelInfo.getInfo(npService, url_)
        val tabs = channelInfo.tabs
        val titles = mutableListOf<String>()
        for (i in tabs.indices) {
            val t = channelInfo.tabs[i]
            var url = t.url
            Log.d(TAG, "feedsTitlesAtUrl url: $url ${t.originalUrl} ${t.baseUrl}")
            if (!url.startsWith("http")) url = url_ + url
            try {
                val urlEnd = Url(url).encodedPath.split("/").last()
//                if (urlEnd != "playlists" && urlEnd != "shorts") titles.add(urlEnd)
                titles.add(urlEnd)
            } catch (e: Exception) { Log.e(TAG, "ytChannelValidTabs tab url not valid: $url") }
        }
        return titles.toList()
    }

    companion object {
        private const val TAG = "YTProvider"
    }
}
