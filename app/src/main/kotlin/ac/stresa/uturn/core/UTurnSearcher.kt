package ac.stresa.uturn.core

import ac.mdiq.podcini.sources.IFeedSearchProvider
import ac.mdiq.podcini.shared.FeedSearchResult
import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.search.SearchInfo

class UTurnSearcher : IFeedSearchProvider.Stub() {
    override fun search(query: String): List<FeedSearchResult> {
        fun fromChannelInfoItem(info: ChannelInfoItem): FeedSearchResult {
            val title = info.name
            val imageUrl: String? = if (info.thumbnails.isNotEmpty()) info.thumbnails[0].url else null
            val feedUrl = info.url
            val author = ""
            val count: Int = info.streamCount.toInt()
            val update: String? = null
            val subscriberCount = info.subscriberCount.toInt()
            return FeedSearchResult(title, imageUrl, feedUrl, author, count, update, subscriberCount, "UT.urn")
        }

        val service = try { NewPipe.getService("YouTube") } catch (e: ExtractionException) { throw ExtractionException("YouTube service not found") }
        try {
            val searchInfo = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query, listOf("channels"), ""))
            val podResults: MutableList<FeedSearchResult> = mutableListOf()
            for (ch in searchInfo.relatedItems) podResults.add(fromChannelInfoItem(ch as ChannelInfoItem))
            return podResults
        } catch (e: Throwable) { Log.e("UTurnSearcher", "error: ${e.message}") }
        return listOf()
    }

    override fun lookupUrl(url: String): String = url

    override fun urlNeedsLookup(url: String): Boolean = false
    override fun getName(): String? = "UT.urn"
}