package ac.stresa.uturn.core

import ac.mdiq.podcini.shared.AudioSpec
import ac.mdiq.podcini.shared.CaptionSpec
import ac.mdiq.podcini.shared.EpisodeIPC
import ac.mdiq.podcini.shared.FeedIPC
import ac.mdiq.podcini.shared.HTTPAttribs
import ac.mdiq.podcini.shared.MediaSpecs
import ac.mdiq.podcini.shared.USER_AGENT
import ac.mdiq.podcini.shared.prepareUrl
import ac.mdiq.podcini.sources.Provider
import ac.roma.npeconnector.FeedBuilder
import ac.roma.npeconnector.InfoCache
import ac.roma.npeconnector.getSortedVStreams
import ac.roma.npeconnector.toAudioSpec
import ac.roma.npeconnector.toCaptionSpec
import ac.roma.npeconnector.toEpisodeIPC
import android.util.Log
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URL

class UTurnProvider: Provider.Stub() {
    private val serviceId = 0
    private val npService by lazy { NewPipe.getService(serviceId) }

    private val CACHE: InfoCache = InfoCache.instance

    override fun httpAtribsFor(url: String): HTTPAttribs {
        val ua = when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
            else -> USER_AGENT
        }
        return HTTPAttribs(ua, "https://www.youtube.com", "https://www.youtube.com")
    }

    override fun canHandleUrl(url_: String): Int {
        val url = try { URL(url_) } catch (e: Exception) { return -1 }
        return if (YoutubeParsingHelper.isYoutubeURL(url) || YoutubeParsingHelper.isYoutubeServiceURL(url)) 1 else -1
    }

    override fun buildEpisode(url: String): EpisodeIPC? {
        return getStreamInfo(url)?.toEpisodeIPC()
    }

    override fun getEpisodeDescription(url: String): String? {
        return getStreamInfo(url)?.description?.content
    }

    override fun getCaptionSpecs(media: EpisodeIPC): List<CaptionSpec> {
        var cSpecs = listOf<CaptionSpec>()
        val subs = getStreamInfo(media.downloadUrl)?.subtitles
        if (subs != null) cSpecs = subs.mapNotNull { it.toCaptionSpec() }.toList()
        return cSpecs
    }

    override fun getMediaSpecs(media: EpisodeIPC, force: Boolean): MediaSpecs {
        val mediaSpecs = MediaSpecs()
        val streamInfo = getStreamInfo(media.downloadUrl, force) ?: return mediaSpecs
        var aSpecs = listOf<AudioSpec>()
        val audioStreams = streamInfo.audioStreams
        if (audioStreams != null) {
            val collectedStreams = mutableSetOf<AudioSpec>()
            for (stream in audioStreams) {
                //                Log.d(TAG, "getFilteredAStreams stream: ${stream.audioTrackId} ${stream.bitrate} ${stream.deliveryMethod} ${stream.format}")
//                if (stream == null || stream.deliveryMethod == DeliveryMethod.TORRENT || (stream.deliveryMethod == DeliveryMethod.HLS && stream.format == MediaFormat.OPUS)) continue
                if (stream == null || stream.deliveryMethod == DeliveryMethod.TORRENT) continue
                collectedStreams.add(stream.toAudioSpec())
            }
            aSpecs = collectedStreams.toList().sortedWith(compareBy { it.bitrate })
        }
        val vSpecs = getSortedVStreams(listOf(), streamInfo.videoOnlyStreams, ascendingOrder = true, preferVideoOnlyStreams = true)
        val mSpecs = getSortedVStreams(streamInfo.videoStreams, listOf(), ascendingOrder = true, preferVideoOnlyStreams = false)
        mediaSpecs.audioSpecs = aSpecs
        mediaSpecs.videoSpecs = vSpecs
        mediaSpecs.muxedSpecs = mSpecs
        return mediaSpecs
    }

    internal fun getStreamInfo(url: String?, forceLoad: Boolean = false): StreamInfo? {
        if (url.isNullOrBlank()) return null
//        Log.d(TAG, "getStreamInfo streamInfo==null: ${streamInfo == null}")
        val cacheType = InfoCache.Type.STREAM
        return if (forceLoad) {
            CACHE.removeInfo(serviceId, url, cacheType)
            StreamInfo.getInfo(npService, url).also { info -> CACHE.putInfo(serviceId, url, info, cacheType) }
        } else {
            val cachedData = CACHE.getFromKey(serviceId, url, cacheType) as? StreamInfo
            cachedData ?: StreamInfo.getInfo(npService, url).also { info -> CACHE.putInfo(serviceId, url, info, cacheType) }
        }
    }

    private fun isChannel(url: String): Boolean {
        try {
            val uURL =  Url(url)
            return uURL.encodedPath.startsWith("/channel") || uURL.encodedPath.startsWith("/@")
        } catch (e: Exception) {
            Log.e(TAG, "isYTChannel urlInit is not valid $url")
            return false
        }
    }

    private fun isPlaylist(url: String): Boolean {
        try { return Url(url).encodedPath.startsWith("/playlist")
        } catch (e: Exception) {
            Log.e(TAG, "isYTChannel urlInit is not valid $url")
            return false
        }
    }

    private var fb: FeedBuilder? = null

    override fun buildFeed(url: String, index: Int): FeedIPC? {
        fb = FeedBuilder(FEEDTYPE, url, npService)
        return runBlocking(Dispatchers.IO) {
            when {
                isChannel(url) -> {
                    fb?.channelInfo = ChannelInfo.getInfo(npService, url)
                    fb?.feedFromChannel(index, "")
                }
                isPlaylist(url) -> if (index == 0) fb?.feedFromPlaylist() else null
                else -> null
            }
        }
    }

    override fun getEpisodes(total: Int, since: Long): List<EpisodeIPC> {
        if (fb == null) return listOf()
        return runBlocking(Dispatchers.IO) {
            when {
                isChannel(fb!!.urlInit) -> fb!!.episodesFromChannel(total, since)
                isPlaylist(fb!!.urlInit) -> fb!!.episodesFromList(total, since)
                else -> listOf()
            }
        }
    }

    override fun feedToUpdate(url: String): FeedIPC? {
        var feed_: FeedIPC?
        when {
            isChannel(url) -> {
                fb = FeedBuilder(FEEDTYPE, url, npService)
                fb?.channelInfo = ChannelInfo.getInfo(npService, url)
//                Log.d(TAG, "feedToUpdate channelInfo: ${fb?.channelInfo} ${fb?.channelInfo?.tabs?.size}")
                runBlocking(Dispatchers.IO) { feed_ = fb?.feedFromChannel(0, "") }
            }
            isPlaylist(url) -> runBlocking(Dispatchers.IO) {
                fb = FeedBuilder(FEEDTYPE, url, npService)
                feed_ = fb?.feedFromPlaylist()
            }
            else -> {
                // channel tabs other than videos
                val uURL =  Url(url)
                val pathSegments = uURL.encodedPath.split("/")
                val channelUrl = "https://www.youtube.com/channel/${pathSegments[1]}"
                val channelInfo = ChannelInfo.getInfo(npService, channelUrl)
                fb = FeedBuilder(FEEDTYPE, channelUrl, npService)
                fb?.channelInfo = channelInfo
                if (channelInfo?.tabs.isNullOrEmpty()) return null
                var index = -1
                var urlEnd = ""
                for (i in channelInfo.tabs.indices) {
                    urlEnd = Url(channelInfo.tabs[i].url).encodedPath.split("/").last()
                    val url_ = prepareUrl(channelInfo.tabs[i].url)
                    if (url == url_) {
                        index = i
                        break
                    }
                }
                if (index < 0) return null
                runBlocking(Dispatchers.IO) {
                    feed_ = fb?.feedFromChannel(index, "")
                    if (feed_ != null && urlEnd.isNotBlank()) feed_.title = "${feed_.title}: $urlEnd"
                }
            }
        }
        feed_?.id = 0L
        return feed_
    }

    override fun feedsTitlesAtUrl(url_: String): List<String> {
        if (!isChannel(url_)) return listOf()
        val channelInfo = ChannelInfo.getInfo(npService, url_)
        val tabs = channelInfo.tabs
        val titles = mutableListOf<String>()
        for (i in tabs.indices) {
            val t = channelInfo.tabs[i]
            var url = t.url
//            Log.d(TAG, "feedsTitlesAtUrl url: $url ${t.originalUrl} ${t.baseUrl}")
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
        const val FEEDTYPE = "YouTube"
    }
}
