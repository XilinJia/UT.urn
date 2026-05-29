package ac.stresa.uturn.core

import ac.mdiq.podcini.sources.Provider
import ac.mdiq.podcini.shared.AudioSpec
import ac.mdiq.podcini.shared.EpisodeIPC
import ac.mdiq.podcini.shared.FeedIPC
import ac.mdiq.podcini.shared.FeedType
import ac.mdiq.podcini.shared.ShareType
import ac.mdiq.podcini.shared.VideoSpec
import ac.mdiq.podcini.shared.prepareUrl
import ac.stresa.uturn.core.FeedBuilder.Companion.EPISODES_LIMIT
import ac.stresa.uturn.core.FeedBuilder.Companion.episodeFrom
import ac.stresa.uturn.core.util.InfoCache
import android.util.Log
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URL

class UTurnProvider: Provider.Stub() {
    private val CACHE: InfoCache = InfoCache.instance

    override fun haveMultiQualities(): Boolean = true

    override fun canHandleUrl(url_: String): Boolean {
        val url = try { URL(url_) } catch (e: Exception) { return false }
        return (YoutubeParsingHelper.isYoutubeURL(url) && url.path.startsWith("/watch")) || YoutubeParsingHelper.isYoutubeServiceURL(url)
    }

    override fun canHandleFeed(url_: String): Boolean {
        val url = try { URL(url_) } catch (e: Exception) { return false }
        return YoutubeParsingHelper.isYoutubeURL(url) || YoutubeParsingHelper.isYoutubeServiceURL(url)
    }

    override fun haveViewCount(): Boolean = true
    override fun haveLikeCount(): Boolean = true

    override fun buildEpisode(url: String): EpisodeIPC? {
        val info = StreamInfo.getInfo(NewPipe.getService(0), url)
        return episodeFrom(info)
    }

//    override fun cleanData() {
//        InfoCache.instance.trimCache()    // TODO
//    }

    override fun getEpisodeDescription(url: String): String? {
        return getStreamInfo(url)?.description?.content
    }

    override fun isFeedAutoDownloadable(urlString: String): Boolean {
        val url = try { URL(urlString) } catch (e: Exception) {
            Log.e(TAG, "isFeedAutoDownloadable url wrong format: $urlString")
            return false
        }
        return !YoutubeParsingHelper.isYoutubeURL(url)
    }

    override fun searcherTAG(): String = "YouTube search"

    override fun canHandleSharedMedia(urlString: String): Boolean {
        val url = try { URL(urlString) } catch (e: Exception) {
            Log.e(TAG, "canHandleSharedMedia url wrong format: $urlString")
            return false
        }
        return (YoutubeParsingHelper.isYoutubeURL(url) && (url.path.startsWith("/watch") || url.path.startsWith("/live"))) || YoutubeParsingHelper.isYoutubeServiceURL(url)
    }

    override fun getShareLogType(): String? = ShareType.YTMedia.name

    override fun feedDomains(): List<String> {
        return listOf("youtube", "youtu.be")
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
        val serviceId = 0
        val cacheType = InfoCache.Type.STREAM
        val streamInfo = StreamInfo.getInfo(NewPipe.getService(serviceId), url).also { info -> CACHE.putInfo(serviceId, url, info, cacheType) }
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

    fun isYTChannel(url: String): Boolean {
        try { return !Url(url).encodedPath.startsWith("/playlist")
        } catch (e: Exception) {
            Log.e(TAG, "isYTChannel urlInit is not valid $url")
            return false
        }
    }

    var fb: FeedBuilder? = null

    override fun buildFeed(url: String, feedSource: String, index: Int): FeedIPC? {
        fb = FeedBuilder(url, feedSource)
        return runBlocking(Dispatchers.IO) {
            when {
                isYTChannel(url) -> {
                    fb?.channelInfo = ChannelInfo.getInfo(NewPipe.getService(0), url)
                    fb?.buildYTChannel(index, "")
                }
                index == 0 -> fb?.buildYTPlaylist()
                else -> null
            }
        }
    }

    override fun getEpisodes(total: Int): List<EpisodeIPC> {
        if (fb == null) return listOf()
        return runBlocking(Dispatchers.IO) {
            when {
                isYTChannel(fb!!.urlInit) -> fb!!.episodesFromChannel(total)
                else -> fb!!.episodesFromList(total)
            }
        }
    }

    override fun downloadFeed(url: String, lastUpdateTime: Long, fullUpdate: Boolean, limitEpisodesCount: Int): FeedIPC? {
        fun setupFeed(channelInfo: ChannelInfo): FeedIPC {
            val feed_ = FeedIPC()
            feed_.downloadUrl = url
            feed_.type = FeedType.YOUTUBE.name
            feed_.hasVideoMedia = true
            feed_.title = channelInfo.name
            feed_.description = channelInfo.description
            feed_.author = channelInfo.parentChannelName
            feed_.imageUrl = if (channelInfo.avatars.isNotEmpty()) channelInfo.avatars.first().url else null
            return feed_
        }
        val service = NewPipe.getService("YouTube")
        val uURL =  Url(url)
        var feed_: FeedIPC?
        when {
            uURL.encodedPath.startsWith("/channel") || uURL.encodedPath.startsWith("/@") -> {
                val channelInfo = ChannelInfo.getInfo(service, url)
                Log.d(TAG, "refreshYoutubeFeed channelInfo: $channelInfo ${channelInfo.tabs.size}")
                if (channelInfo.tabs.isEmpty()) {
                    //                onFail(feed, "feed channelInfo is empty: ${feed.title}")  // TODO
                    return null
                }
                val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs.first())
                Log.d(TAG, "refreshYoutubeFeed result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
                var infoItems = channelTabInfo.relatedItems
                var nextPage = channelTabInfo.nextPage
                val eList = mutableSetOf<EpisodeIPC>()
                var count = 0
                while (infoItems.isNotEmpty()) {
                    for (r_ in infoItems) {
                        val r = r_ as StreamInfoItem
                        count++
                        if (r.infoType != InfoItem.InfoType.STREAM) continue
                        //                                Log.d(TAG, "item: ${r.uploadDate?.date()?.time} ${r.textualUploadDate} ${r.name}")
                        if (fullUpdate || (r.uploadDate?.localDateTime?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: 0) > lastUpdateTime) eList.add(episodeFrom(r))
                        else {
                            nextPage = null
                            break
                        }
                    }
                    if (nextPage == null || count > 2*EPISODES_LIMIT || eList.size > EPISODES_LIMIT) break
                    if (limitEpisodesCount > 0 && count > 1.2*limitEpisodesCount) break
                    val page = ChannelTabInfo.getMoreItems(service, channelInfo.tabs.first(), nextPage)
                    nextPage = page.nextPage
                    infoItems = page.items
                    Log.d(TAG, "refreshYoutubeFeed more infoItems: ${infoItems.size} ${eList.size}")
                }
                feed_ = setupFeed(channelInfo)
                feed_.episodes = eList.toMutableList()
            }
            uURL.encodedPath.startsWith("/playlist") -> {
                val playlistInfo = PlaylistInfo.getInfo(service, url)
                if (playlistInfo == null) {
                    //                onFail(feed, "feed playlistInfo is null: ${feed.title}")
                    return null
                }
                val eList = mutableSetOf<EpisodeIPC>()

                var infoItems = playlistInfo.relatedItems
                var nextPage = playlistInfo.nextPage
                var count = 0
                while (infoItems.isNotEmpty()) {
                    for (r in infoItems) {
                        count++
                        if (r.infoType != InfoItem.InfoType.STREAM) continue
                        //                                Log.d(TAG, "item: ${r.uploadDate?.date()?.time} ${r.name}")
                        if (fullUpdate || (r.uploadDate?.localDateTime?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: 0) > lastUpdateTime) eList.add(episodeFrom(r))
                        else {
                            nextPage = null
                            break
                        }
                    }
                    if (nextPage == null || count > 2*EPISODES_LIMIT || eList.size > EPISODES_LIMIT) break
                    if (limitEpisodesCount > 0 && count > 1.2*limitEpisodesCount) break
                    val page = PlaylistInfo.getMoreItems(service, url, nextPage) ?: break
                    nextPage = page.nextPage
                    infoItems = page.items
                    Log.d(TAG, "more infoItems: ${infoItems.size} ${eList.size}")
                }
                feed_ = FeedIPC()
                feed_.downloadUrl = url
                feed_.type = FeedType.YOUTUBE.name
                feed_.hasVideoMedia = true
                feed_.title = playlistInfo.name
                feed_.description = playlistInfo.description?.content ?: ""
                feed_.author = playlistInfo.uploaderName
                feed_.imageUrl = if (playlistInfo.thumbnails.isNotEmpty()) playlistInfo.thumbnails.first().url else null
                feed_.episodes = eList.toMutableList()
            }
            else -> {
                // channel tabs other than videos
                val pathSegments = uURL.encodedPath.split("/")
                val channelUrl = "https://www.youtube.com/channel/${pathSegments[1]}"
                Log.d(TAG, "channelUrl: $channelUrl")
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)
                Log.d(TAG, "refreshYoutubeFeed channelInfo: $channelInfo ${channelInfo.tabs.size}")
                if (channelInfo.tabs.isEmpty()) {
                    //                onFail(feed, "feed channelInfo is empty: ${feed.title}")
                    return null
                }
                var index = -1
                for (i in channelInfo.tabs.indices) {
                    val url_ = prepareUrl(channelInfo.tabs[i].url)
                    if (url == url_) {
                        index = i
                        break
                    }
                }
                if (index < 0) {
                    //                onFail(feed, "feed channelInfo tabs index is invalid: ${feed.title}")
                    return null
                }
                val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs[index])
                Log.d(TAG, "refreshYoutubeFeed result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
                var infoItems = channelTabInfo.relatedItems
                var nextPage = channelTabInfo.nextPage
                val eList = mutableSetOf<EpisodeIPC>()
                var count = 0
                while (infoItems.isNotEmpty()) {
                    for (r_ in infoItems) {
                        val r = r_ as StreamInfoItem
                        count++
                        if (r.infoType != InfoItem.InfoType.STREAM) continue
                        //                                Log.d(TAG, "item: ${r.uploadDate?.date()?.time} ${r.name}")
                        if (fullUpdate || (r.uploadDate?.localDateTime?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: 0) > lastUpdateTime) eList.add(episodeFrom(r))
                        else {
                            nextPage = null
                            break
                        }
                    }
                    if (nextPage == null || count > 2*EPISODES_LIMIT || eList.size > EPISODES_LIMIT) break
                    if (limitEpisodesCount > 0 && count > 1.2*limitEpisodesCount) break
                    val page = ChannelTabInfo.getMoreItems(service, channelInfo.tabs[index], nextPage)
                    nextPage = page.nextPage
                    infoItems = page.items
                    Log.d(TAG, "refreshYoutubeFeed more infoItems: ${infoItems.size}")
                }
                Log.d(TAG, "refreshYoutubeFeed eList.size: ${eList.size}")
                feed_ = setupFeed(channelInfo)
                feed_.episodes = eList.toMutableList()
            }
        }
        return feed_
    }

    override fun feedsAtUrl(url_: String): List<String> {
        if (!isYTChannel(url_)) return listOf()
        val channelInfo = ChannelInfo.getInfo(NewPipe.getService(0), url_)
        val tabs = channelInfo.tabs
        val titles = mutableListOf<String>()
        for (i in tabs.indices) {
            val t = channelInfo.tabs[i]
            var url = t.url
            Log.d(TAG, "url: $url ${t.originalUrl} ${t.baseUrl}")
            if (!url.startsWith("http")) url = url_ + url
            try {
                val urlEnd = Url(url).encodedPath.split("/").last()
                if (urlEnd != "playlists" && urlEnd != "shorts") titles.add(urlEnd)
            } catch (e: Exception) { Log.e(TAG, "ytChannelValidTabs tab url not valid: $url") }
        }
        return titles.toList()
    }

    companion object {
        private const val TAG = "YTProvider"
    }
}
