package ac.stresa.uturn.core

import ac.mdiq.podcini.shared.EpisodeIPC
import ac.mdiq.podcini.shared.FeedIPC
import ac.mdiq.podcini.shared.getEntityId
import ac.mdiq.podcini.shared.prepareUrl
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class FeedBuilder(var urlInit: String, private val feedSource: String) {
    private val TAG = "FeedBuilder"

    var selectedDownloadUrl: String? = null

    private val service by lazy { NewPipe.getService("YouTube") }
    internal var channelInfo: ChannelInfo? = null

    var feedId: Long = 0L

    var nextPage: Page? = null
    var playlistInfo: PlaylistInfo? = null
    var streamInfoItems: List<StreamInfoItem> = listOf()

    var curItemIndex: Int = 0

//    var channelTabInfo: ChannelTabInfo? = null
    var infoItems: List<InfoItem> = listOf()

    private fun setupFeed(): FeedIPC {
        val feed_ = FeedIPC()
        feed_.downloadUrl = selectedDownloadUrl
//        feed_.isBuilding = true
        feedId = getEntityId()
        feed_.id = feedId
        feed_.type = FEEDTYPE
        feed_.hasVideoMedia = true
        feed_.prefStreamOverDownload = true
        feed_.episodesDownloadable = false
        feed_.autoDownload = false
        return feed_
    }

    suspend fun episodesFromList(total: Int): List<EpisodeIPC> {
        Log.d(TAG, "buildYTPlaylist infoItems: ${streamInfoItems.size}")
        val titleSet = hashSetOf<String>()
        var count = 0
        val eList = mutableSetOf<EpisodeIPC>()
        while (streamInfoItems.isNotEmpty()) {
            for (i in curItemIndex until  streamInfoItems.size) {
                val r = streamInfoItems[i]
                //                        Log.d(TAG, "buildYTPlaylist relatedItem: $r")
                curItemIndex = i
                if (r.infoType != InfoItem.InfoType.STREAM) {
                    Log.d(TAG, "buildYTPlaylist relatedItem is not STREAM, ignored")
                    continue
                }
                count++
                val e = episodeFrom(r)
                if (e.title == null || e.title in titleSet) continue
                e.feedId = feedId
                eList.add(e)
            }
            Log.d(TAG, "buildYTChannel number of episodes added: ${eList.size}")
            if (nextPage == null || (total > 0 && eList.size > total) || count > EPISODES_LIMIT) break
            try {
                val page = PlaylistInfo.getMoreItems(service, urlInit, nextPage) ?: break
                nextPage = page.nextPage
                streamInfoItems = page.items
                curItemIndex = 0
                Log.d(TAG, "buildYTPlaylist more infoItems: ${streamInfoItems.size}")
            } catch (e: Throwable) {
                Log.d(TAG, "buildYTPlaylist PlaylistInfo.getMoreItems error: ${e.message}")
                withContext(Dispatchers.Main) { return@withContext null }
                break
            }
        }
        return eList.toList()
    }

    internal suspend fun buildYTPlaylist(): FeedIPC? {
        return try {
            playlistInfo = PlaylistInfo.getInfo(service, urlInit) ?: return null
            selectedDownloadUrl = prepareUrl(urlInit)
            Log.d(TAG, "buildYTPlaylist selectedDownloadUrl: $selectedDownloadUrl url: $urlInit")
            val feed_ = setupFeed()
            feed_.title = playlistInfo!!.name
            feed_.description = playlistInfo?.description?.content ?: ""
            feed_.author = playlistInfo?.uploaderName
            feed_.imageUrl = if (playlistInfo!!.thumbnails.isNotEmpty()) playlistInfo!!.thumbnails.first().url else null
            streamInfoItems = playlistInfo!!.relatedItems
            nextPage = playlistInfo?.nextPage
            withContext(Dispatchers.Main) { return@withContext feed_ }
        } catch (e: Throwable) {
            Log.d(TAG, "buildYTPlaylist error ${e.message}")
            withContext(Dispatchers.Main) { return@withContext null }
        }
    }

    suspend fun episodesFromChannel(total: Int): List<EpisodeIPC> {
        val cInfo = channelInfo ?:  return listOf()

        Log.d(TAG, "infoItems: ${infoItems.size}")
//        var nextPage = channelTabInfo?.nextPage
        val titleSet = hashSetOf<String>()
        var count = 0
        val eList = mutableSetOf<EpisodeIPC>()
        while (infoItems.isNotEmpty()) {
            for (i in curItemIndex until  infoItems.size) {
                val r = infoItems[i]
                count++
                curItemIndex = i
                if (r.infoType != InfoItem.InfoType.STREAM) continue
                val e = episodeFrom(r as StreamInfoItem)
                if (e.title == null || e.title in titleSet) continue
                titleSet.add(e.title!!)
                e.feedId = feedId
                eList.add(e)
            }
            Log.d(TAG, "buildYTChannel number of episodes added: ${eList.size}")
            if (nextPage == null || (total > 0 && eList.size > total) || count > 2 * EPISODES_LIMIT || eList.size > EPISODES_LIMIT) break
            try {
                val page = ChannelTabInfo.getMoreItems(service, cInfo.tabs.first(), nextPage!!)
                nextPage = page.nextPage
                infoItems = page.items
                curItemIndex = 0
                Log.d(TAG, "more infoItems: ${infoItems.size}")
            } catch (e: Throwable) {
                Log.d(TAG, "ChannelTabInfo.getMoreItems error: ${e.message}")
                withContext(Dispatchers.Main) { return@withContext null }
                break
            }
        }
        return eList.toList()
    }

    internal suspend fun buildYTChannel(index: Int, title: String): FeedIPC?  {
        val cInfo = channelInfo ?:  return null
        Log.d(TAG, "buildYTChannel result: $cInfo ${cInfo.tabs.size}")
        var url = cInfo.tabs[index].url
        if (!url.startsWith("http")) url = urlInit
        return try {
            selectedDownloadUrl = prepareUrl(url)
            Log.d(TAG, "selectedDownloadUrl: $selectedDownloadUrl url: $url")
            val channelTabInfo = ChannelTabInfo.getInfo(service, cInfo.tabs[index])
            Log.d(TAG, "buildYTChannel result1: $channelTabInfo ${channelTabInfo.relatedItems?.size}")
            val feed_ = setupFeed()
            feed_.title = cInfo.name + " " + title
            feed_.description = cInfo.description
            feed_.author = cInfo.parentChannelName
            feed_.imageUrl = if (cInfo.avatars.isNotEmpty()) cInfo.avatars.first().url else null
            infoItems = channelTabInfo.relatedItems
            nextPage = channelTabInfo.nextPage
            withContext(Dispatchers.Main) { return@withContext feed_ }
        } catch (e: Throwable) {
            Log.d(TAG, "buildYTChannel error1 ${e.message}")
            withContext(Dispatchers.Main) { return@withContext null }
        }
    }

    companion object {
        const val EPISODES_LIMIT = 3000

        const val FEEDTYPE = "YouTube"

        internal fun episodeFrom(item: StreamInfoItem): EpisodeIPC {
            val e = EpisodeIPC()
            e.link = item.url
            e.title = item.name
            e.description = "Short: ${item.shortDescription}"
            e.imageUrl = item.thumbnails.first().url
            e.pubDate = item.uploadDate?.localDateTime?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())?.toEpochMilliseconds() ?: 0
            e.viewCount = item.viewCount.toInt()
            e.size = 0
            e.mimeType = "video/*"
            e.fileUrl = null
            e.downloadUrl = item.url
            if (item.duration > 0) e.duration = item.duration.toInt() * 1000
//            e.likeCount = item.likeCount.toInt()
            // TODO: need to get likeCount
            return e
        }

        fun episodeFrom(info: StreamInfo): EpisodeIPC {
            val e = EpisodeIPC()
            e.link = info.url
            e.title = info.name
            e.description = info.description?.content
            e.imageUrl = info.thumbnails.first().url
            e.pubDate = info.uploadDate?.localDateTime?.let {
                LocalDateTime(year = it.year, month = it.monthValue, day = it.dayOfMonth, hour = it.hour, minute = it.minute, second = it.second, nanosecond = it.nano).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            } ?: 0
            e.viewCount = info.viewCount.toInt()
            e.likeCount = info.likeCount.toInt()
            e.downloadUrl = info.url
            e.size = 0
            e.mimeType = "video/*"
            if (info.duration > 0) e.duration = info.duration.toInt() * 1000
            return e
        }
    }
}